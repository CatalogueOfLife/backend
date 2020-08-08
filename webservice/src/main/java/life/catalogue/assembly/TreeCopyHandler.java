package life.catalogue.assembly;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.CatCopy;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.parser.NameParser;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

import static life.catalogue.api.util.ObjectUtils.coalesce;

public class TreeCopyHandler implements Consumer<NameUsageBase>, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(TreeCopyHandler.class);
  private static List<Rank> IMPLICITS = ImmutableList.of(Rank.GENUS,Rank.SUBGENUS, Rank.SPECIES);

  private final Set<EntityType> entities;
  private final Set<Rank> ranks;
  private final List<Rank> implicitRanks = new ArrayList<>();

  private final int catalogueKey;
  private final User user;
  private final Sector sector;
  private final SectorImport state;
  private final Map<String, EditorialDecision> decisions;
  private final SqlSession session;
  private final SqlSession batchSession;
  private final ReferenceMapper rm;
  private final TaxonMapper tm;
  private final NameMapper nm;
  private int sCounter = 0;
  private int tCounter = 0;
  private int ignoredCounter = 0;
  private final Usage target;
  private final Map<RanKnName, Usage> implicits = new HashMap<>();
  private final Map<String, Usage> ids = new HashMap<>();
  private final Map<String, String> refIds = new HashMap<>();
  
  TreeCopyHandler(Map<String, EditorialDecision> decisions, SqlSessionFactory factory, User user, Sector sector, SectorImport state) {
    this.catalogueKey = sector.getDatasetKey();
    this.user = user;
    this.sector = sector;
    this.state = state;
    this.decisions = decisions;
    // we open up a separate batch session that we can write to so we do not disturb the open main cursor for processing with this handler
    batchSession = factory.openSession(ExecutorType.BATCH, false);
    session = factory.openSession(true);

    this.entities = Preconditions.checkNotNull(sector.getEntities(), "Sector entities required");
    LOG.info("Copy taxon extensions: {}", Joiner.on(", ").join(entities));

    this.ranks = Preconditions.checkNotNull(sector.getRanks(), "Sector ranks required");
    if (ranks.size() < Rank.values().length) {
      LOG.info("Copy only ranks: {}", Joiner.on(", ").join(ranks));
    }

    for (Rank r : IMPLICITS) {
      if (!ranks.isEmpty() && ranks.contains(r)) {
        implicitRanks.add(r);
      }
    }
    LOG.info("Create implicit taxa for ranks {}", Joiner.on(", ").join(implicitRanks));

    rm = batchSession.getMapper(ReferenceMapper.class);
    tm = batchSession.getMapper(TaxonMapper.class);
    nm = batchSession.getMapper(NameMapper.class);
    // load target taxon
    Taxon t = tm.get(sector.getTargetAsDSID());
    target = new Usage(t.getId(), t.getName().getRank(), t.getStatus());
  }

  public void reset() {
    ids.clear();
  }

  private static class Usage {
    String id;
    Rank rank;
    TaxonomicStatus status;
    
    Usage(String id, Rank rank, TaxonomicStatus status) {
      this.id = id;
      this.rank = rank;
      this.status = status;
    }
  }
  private static class RanKnName {
    final Rank rank;
    final String name;
  
    public RanKnName(Rank rank, String name) {
      this.rank = rank;
      this.name = name;
    }
  
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RanKnName ranKnName = (RanKnName) o;
      return rank == ranKnName.rank &&
          Objects.equals(name, ranKnName.name);
    }
  
    @Override
    public int hashCode() {
      return Objects.hash(rank, name);
    }
  }
    
    
  private Usage usage(NameUsageBase u) {
    return new Usage(u.getId(), u.getName().getRank(), u.getStatus());
  }

  private Usage createImplicit(Usage parent, Taxon taxon) {
    List<Rank> neededRanks = new ArrayList<>();
    
    // figure out if we need to create any implicit taxon
    Name origName = taxon.getName();
    if (origName.isParsed() && !origName.isIndetermined()) {
      for (Rank r : implicitRanks) {
        if (parent.rank.higherThan(r) && r.higherThan(origName.getRank())) {
          neededRanks.add(r);
        }
      }
  
      for (Rank r : neededRanks) {
        Name n = new Name();
        n.setCode(origName.getCode());
        if (r == Rank.GENUS) {
          n.setUninomial(origName.getGenus());
        
        } else if (r == Rank.SUBGENUS) {
          if (origName.getInfragenericEpithet() == null) {
            continue;
          }
          n.setUninomial(origName.getInfragenericEpithet());

        } else if (r == Rank.SPECIES) {
          n.setGenus(origName.getGenus());
          n.setSpecificEpithet(origName.getSpecificEpithet());
        } else {
          // just to make sure
          throw new IllegalStateException("Unknown implicit rank " + r);
        }
        n.setRank(r);
        n.setType(NameType.SCIENTIFIC);
        n.setSectorKey(sector.getId());
        n.rebuildScientificName();
        // make sure we have a name: https://github.com/CatalogueOfLife/backend/issues/735
        if (n.getScientificName() == null) {
          LOG.warn("Could not create implicit name for rank {} from {}: {}", r, origName.getScientificName(), n);
          continue;
        }
        RanKnName rnn = new RanKnName(r, n.getScientificName());
        // did we create that implicit name before?
        if (implicits.containsKey(rnn)) {
          parent = implicits.get(rnn);
          continue;
        }
  
        DatasetEntityDao.newKey(n);
        n.setDatasetKey(catalogueKey);
        n.setOrigin(Origin.IMPLICIT_NAME);
        n.applyUser(user);
        LOG.debug("Create implicit {} from {}: {}", r, origName.getScientificName(), n);
        nm.create(n);
  
        Taxon t = new Taxon();
        DatasetEntityDao.newKey(t);
        t.setDatasetKey(catalogueKey);
        t.setName(n);
        t.setParentId(parent.id);
        t.setSectorKey(sector.getId());
        t.setOrigin(Origin.IMPLICIT_NAME);
        t.setStatus(TaxonomicStatus.ACCEPTED);
        t.applyUser(user);
        tm.create(t);
  
        parent = usage(t);
        //reuse implicit names...
        implicits.put(new RanKnName(n.getRank(), n.getScientificName()), parent);
      }
    }
    return parent;
  }

  @Override
  public void accept(NameUsageBase u) {
    u.setSectorKey(sector.getId());
    u.getName().setSectorKey(sector.getId());
    // before we apply a specific decision
    if (sector.getCode() != null) {
      u.getName().setCode(sector.getCode());
    }
    
    if (decisions.containsKey(u.getId())) {
      applyDecision(u, decisions.get(u.getId()));
    }
    if (skipUsage(u)) {
      state.setIgnoredUsageCount(++ignoredCounter);
      // skip this taxon, but include children
      LOG.debug("Ignore {} {} [{}] type={}; status={}", u.getName().getRank(), u.getName().getLabel(), u.getId(), u.getName().getType(), u.getName().getNomStatus());
      // use taxons parent also as the parentID for this so children link one level up
      ids.put(u.getId(), ids.getOrDefault(u.getParentId(), target));
      return;
    }
    
    // all non root nodes have newly created parents
    Usage parent = ids.getOrDefault(u.getParentId(), target);
    if (u.isTaxon() && u.getName().getRank().isSpeciesOrBelow()) {
      // make sure we have a genus for species and a species for infraspecific taxa
      parent = createImplicit(parent, (Taxon) u);
    }

    // copy usage with all associated information. This assigns a new id !!!
    DSID<String> orig;
    DSID<String> parentDID = new DSIDValue<>(catalogueKey, parent.id);
    orig = CatCopy.copyUsage(batchSession, session, u, parentDID, user.getKey(), true, entities, this::lookupReference, this::lookupReference);
    // remember old to new id mapping
    ids.put(orig.getId(), usage(u));
    // counter
    if (u.isTaxon()) {
      state.setTaxonCount(++tCounter);
    } else {
      state.setSynonymCount(++sCounter);
    }
    
    // commit in batches
    if ((sCounter + tCounter) % 1000 == 0) {
      session.commit();
      batchSession.commit();
    }
  }
  
  private boolean skipUsage(NameUsageBase u) {
    Name n = u.getName();
    if (!ranks.isEmpty() && !ranks.contains(n.getRank())) {
      return true;
    }
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
          return true;
      }
    }
    if (n.getCultivarEpithet() != null || n.getCode() == NomCode.CULTIVARS || n.getRank().isCultivarRank()) {
      return true;
    }
    if (n.getType().isParsable() && n.isIndetermined()) {
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
          
          if (n2.getScientificName() != null) {
            // parse a new name!
            final String name = n2.getScientificName() + " " + coalesce(n2.getAuthorship(), "");
            NomCode code = coalesce(n2.getCode(), n.getCode());
            Rank rank = coalesce(n2.getRank(), n.getRank());
            ParsedNameUsage nat = NameParser.PARSER.parse(name, rank, code, IssueContainer.VOID).orElseGet(() -> {
              LOG.warn("Unparsable decision name {}", name);
              // add the full, unparsed authorship in this case to not lose it
              ParsedNameUsage nat2 = new ParsedNameUsage();
              nat2.getName().setScientificName(n2.getScientificName());
              nat2.getName().setAuthorship(n2.getAuthorship());
              return nat2;
            });
            // copy all pure name props
            Name nn = nat.getName();
            n.setScientificName(nn.getScientificName());
            n.setAuthorship(nn.getAuthorship());
            n.setType(nn.getType());
            n.setRank(nn.getRank());
            n.setUninomial(nn.getUninomial());
            n.setGenus(nn.getGenus());
            n.setInfragenericEpithet(nn.getInfragenericEpithet());
            n.setSpecificEpithet(nn.getSpecificEpithet());
            n.setInfraspecificEpithet(nn.getInfraspecificEpithet());
            n.setCultivarEpithet(nn.getCultivarEpithet());
            n.setCandidatus(nn.isCandidatus());
            n.setNotho(nn.getNotho());
            n.setCombinationAuthorship(nn.getCombinationAuthorship());
            n.setBasionymAuthorship(nn.getBasionymAuthorship());
            n.setSanctioningAuthor(nn.getSanctioningAuthor());

          } else if (n2.getAuthorship() != null) {
            // no full name, just changing authorship
            n.setAuthorship(n2.getAuthorship());
            ParsedAuthorship an = NameParser.PARSER.parseAuthorship(n2.getAuthorship()).orElseGet(() -> {
              LOG.warn("Unparsable decision authorship {}", n2.getAuthorship());
              // add the full, unparsed authorship in this case to not lose it
              ParsedName pn2 = new ParsedName();
              pn2.getCombinationAuthorship().getAuthors().add(n2.getAuthorship());
              return pn2;
            });
            n.setCombinationAuthorship(an.getCombinationAuthorship());
            n.setSanctioningAuthor(an.getSanctioningAuthor());
            n.setBasionymAuthorship(an.getBasionymAuthorship());
          }
          // any other changes
          if (n2.getCode() != null) {
            n.setCode(n2.getCode());
          }
          if (n2.getRank() != null && Rank.UNRANKED != n2.getRank()) {
            // unranked is the default
            n.setRank(n2.getRank());
          }
          if (n2.getNomStatus() != null) {
            n.setNomStatus(n2.getNomStatus());
          }
          if (n2.getType() != null) {
            n.setType(n2.getType());
          }
        }
        if (ed.getStatus() != null) {
          try {
            u.setStatus(ed.getStatus());
          } catch (IllegalArgumentException e) {
            LOG.warn("Cannot convert {} {} {} into {}", u.getName().getRank(), u.getStatus(), u.getName().getLabel(), ed.getStatus(), e);
          }
        }
        if (u.isTaxon()) {
          Taxon t = (Taxon) u;
          if (ed.getLifezones() != null) {
            t.setLifezones(ed.getLifezones());
          }
          if (ed.isExtinct() != null) {
            t.setExtinct(ed.isExtinct());
          }
        }
      case REVIEWED:
        // good. nothing to do
    }
    // propagate all notes to usage remarks
    // https://github.com/CatalogueOfLife/backend/issues/740
    if (ed.getNote() != null) {
      u.addRemarks(ed.getNote());
    }
  }
  
  private String lookupReference(String refID) {
    if (refID != null) {
      if (refIds.containsKey(refID)) {
        // we have seen this ref before
        return refIds.get(refID);
      }
      // not seen before, load full reference
      Reference r = rm.get(new DSIDValue<>(sector.getSubjectDatasetKey(), refID));
      if (r != null) {
        return lookupReference(r);
      } else {
        LOG.warn("Reference {} is missing in source dataset {}", refID, sector.getSubjectDatasetKey());
        refIds.put(refID, null);
      }
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
      List<Reference> matches = rm.find(catalogueKey, sector.getId(), ref.getCitation());
      if (matches.isEmpty()) {
        // insert new ref
        ref.setDatasetKey(catalogueKey);
        ref.setSectorKey(sector.getId());
        ref.applyUser(user);
        DSID<String> origID = ReferenceDao.copyReference(batchSession, ref, catalogueKey, user.getKey());
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
    batchSession.commit();
    batchSession.close();
  }
}
