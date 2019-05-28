package org.col.assembly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.apache.ibatis.session.*;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.EntityType;
import org.col.dao.ReferenceDao;
import org.col.dao.SynonymDao;
import org.col.dao.TaxonDao;
import org.col.db.mapper.ReferenceMapper;
import org.col.parser.NameParser;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeCopyHandler implements ResultHandler<NameUsageBase>, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(TreeCopyHandler.class);
  private static Set<EntityType> COPY_DATA = ImmutableSet.of(
      EntityType.REFERENCE,
      EntityType.VERNACULAR,
      EntityType.DISTRIBUTION,
      EntityType.REFERENCE
  );
  
  private final int catalogueKey = Datasets.DRAFT_COL;
  private final ColUser user;
  private final Sector sector;
  private final SectorImport state;
  private final Map<String, EditorialDecision> decisions;
  private final SqlSession session;
  private final ReferenceMapper rMapper;
  private int counter = 0;
  private final Map<String, String> ids = new HashMap<>();
  private final Map<String, String> refIds = new HashMap<>();
  
  TreeCopyHandler(SqlSessionFactory factory, ColUser user, Sector sector, SectorImport state, Map<String, EditorialDecision> decisions) {
    this.user = user;
    this.sector = sector;
    this.state = state;
    this.decisions = decisions;
    // we open up a separate batch session that we can write to so we do not disturb the open main cursor for processing with this handler
    this.session = factory.openSession(ExecutorType.BATCH, false);
    rMapper = session.getMapper(ReferenceMapper.class);
  }
  
  @Override
  public void handleResult(ResultContext<? extends NameUsageBase> ctxt) {
    NameUsageBase u = ctxt.getResultObject();
    u.setSectorKey(sector.getKey());
    u.getName().setSectorKey(sector.getKey());
    
    if (decisions.containsKey(u.getId())) {
      applyDecision(u, decisions.get(u.getId()));
    }
    if (skipUsage(u)) {
      // skip this taxon, but include children
      LOG.debug("Ignore {} [{}] type={}; status={}", u.getName().scientificNameAuthorship(), u.getId(), u.getName().getType(), u.getName().getNomStatus());
      // use taxons parent also as the parentID for this so children link one level up
      ids.put(u.getId(), ids.get(u.getParentId()));
      return;
    }
    
    String parentID;
    // treat root node according to sector mode
    if (sector.getSubject().getId().equals(u.getId())) {
      if (sector.getMode() == Sector.Mode.MERGE) {
        // in merge mode the root node itself is not copied
        // but all child taxa should be linked to the sector target, so remember ID:
        ids.put(u.getId(), sector.getTarget().getId());
        return;
      }
      // we want to attach the root node under the sector target
      parentID = sector.getTarget().getId();
    } else {
      // all non root nodes have newly created parents
      parentID = ids.get(u.getParentId());
    }
    DatasetID parent = new DatasetID(catalogueKey, parentID);
    
    // copy usage with all associated information. This assigns a new id !!!
    DatasetID orig;
    if (u.isTaxon()) {
      orig = TaxonDao.copyTaxon(session, (Taxon) u, parent, user.getKey(), COPY_DATA, this::lookupReference, this::lookupReference);
    } else {
      orig = SynonymDao.copySynonym(session, (Synonym) u, parent, user.getKey(), this::lookupReference);
    }
    // remember old to new id mapping
    ids.put(orig.getId(), u.getId());
    
    // commit in batches
    if (counter++ % 1000 == 0) {
      session.commit();
    }
    state.setTaxonCount(counter);
  }
  
  private boolean skipUsage(NameUsageBase u) {
    Name n = u.getName();
    
    switch (n.getType()) {
      case PLACEHOLDER:
      case NO_NAME:
      case HYBRID_FORMULA:
      case INFORMAL:
        return true;
    }
    if (n.getNomStatus() != null) {
      switch (n.getNomStatus()) {
        case CHRESONYM:
        case MANUSCRIPT:
          return true;
      }
    }
    if (n.getCultivarEpithet() != null || n.getCode() == NomCode.CULTIVARS || n.getRank().isCultivarRank()) {
      return true;
    }
    if (n.isIndetermined()) {
      return true;
    }
    return false;
  }
  
  private void applyDecision(NameUsageBase u, EditorialDecision ed) {
    switch (ed.getMode()) {
      case BLOCK:
        throw new IllegalStateException("Blocked usage " + u.getId() + " should not have been traversed");
      case UPDATE:
        if (ed.getName() != null) {
          Name n = u.getName();
          Name n2 = ed.getName();
          if (n2.getCode() != null) {
            n.setCode(n2.getCode());
          }
          if (n2.getNomStatus() != null) {
            n.setNomStatus(n2.getNomStatus());
          }
          if (n2.getType() != null) {
            n.setType(n2.getType());
          }
          if (n2.getRank() != null) {
            n.setRank(n2.getRank());
          }
          if (n2.getAuthorship() != null) {
            n.setAuthorship(n2.getAuthorship());
            ParsedName pn = NameParser.PARSER.parseAuthorship(n2.getAuthorship()).orElseGet(() -> {
              LOG.warn("Unparsable decision authorship {}", n2.getAuthorship());
              // add the full, unparsed authorship in this case to not lose it
              ParsedName pn2 = new ParsedName();
              pn2.getCombinationAuthorship().getAuthors().add(n2.getAuthorship());
              return pn2;
            });
            n.setCombinationAuthorship(pn.getCombinationAuthorship());
            n.setSanctioningAuthor(pn.getSanctioningAuthor());
            n.setBasionymAuthorship(pn.getBasionymAuthorship());
          }
        }
        if (ed.getStatus() != null) {
          try {
            u.setStatus(ed.getStatus());
          } catch (IllegalArgumentException e) {
            LOG.warn("Cannot convert {} {} {} into {}", u.getName().getRank(), u.getStatus(), u.getName().canonicalNameComplete(), ed.getStatus(), e);
          }
        }
        if (u.isTaxon()) {
          Taxon t = (Taxon) u;
          if (ed.getLifezones() != null) {
            t.setLifezones(ed.getLifezones());
          }
          if (ed.getFossil() != null) {
            t.setFossil(ed.getFossil());
          }
          if (ed.getRecent() != null) {
            t.setRecent(ed.getRecent());
          }
        }
      case REVIEWED:
        // good. nothing to do
    }
  }
  
  private String lookupReference(String refID) {
    if (refID != null) {
      if (refIds.containsKey(refID)) {
        // we have seen this ref before
        return refIds.get(refID);
      }
      // not seen before, load full reference
      Reference r = rMapper.get(sector.getDatasetKey(), refID);
      return lookupReference(r);
    }
    return null;
  }
  
  private String lookupReference(Reference ref) {
    if (ref != null) {
      if (refIds.containsKey(ref.getId())) {
        // we have seen this ref before
        return refIds.get(ref.getId());
      }
      // sth new?
      List<Reference> matches = rMapper.find(catalogueKey, sector.getKey(), ref.getCitation());
      if (matches.isEmpty()) {
        // insert new ref
        ref.setDatasetKey(catalogueKey);
        ref.setSectorKey(sector.getKey());
        ref.applyUser(user);
        DatasetID origID = ReferenceDao.copyReference(session, ref, catalogueKey, user.getKey());
        refIds.put(origID.getId(), ref.getId());
        return ref.getId();
        
      } else {
        if (matches.size() > 1) {
          LOG.warn("{} duplicate references in catalogue {} with citation {}", matches.size(), catalogueKey, ref.getCitation());
        }
        String refID = matches.get(0).getId();
        refIds.put(ref.getId(), refID);
        return refID;
      }
    }
    return null;
  }
  
  @Override
  public void close() {
    session.commit();
    session.close();
  }
}
