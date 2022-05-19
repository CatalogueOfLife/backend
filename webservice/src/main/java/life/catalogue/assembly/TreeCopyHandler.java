package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.func.ThrowingConsumer;
import life.catalogue.dao.CatCopy;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.NameIndex;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.*;

import java.util.*;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import static life.catalogue.api.util.ObjectUtils.coalesce;

public class TreeCopyHandler implements ThrowingConsumer<NameUsageBase, InterruptedException>, AutoCloseable {
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
  private final NameIndex nameIndex;
  private final SqlSession session;
  private final SqlSession batchSession;
  private final VerbatimSourceMapper vm;
  private final ReferenceMapper rm;
  private final TaxonMapper tm;
  private final NameMapper nm;
  // for reading only:
  private final NameUsageMapper num;
  private int sCounter = 0;
  private int tCounter = 0;
  private final Usage target;
  private String lastParentID;
  private final List<NameUsageBase> lastUsages = new ArrayList<>();
  private final Map<RanKnName, Usage> implicits = new HashMap<>();
  private final Map<String, Usage> ids = new HashMap<>();
  private final Map<String, String> refIds = new HashMap<>();
  private final Map<String, String> nameIds = new HashMap<>();
  final Map<IgnoreReason, Integer> ignoredCounter = new HashMap<>();
  int decisionCounter = 0;

  TreeCopyHandler(Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, User user, Sector sector, SectorImport state) {
    this.catalogueKey = sector.getDatasetKey();
    this.user = user;
    this.sector = sector;
    this.state = state;
    this.decisions = decisions;
    this.nameIndex = nameIndex;
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

    vm = batchSession.getMapper(VerbatimSourceMapper.class);
    rm = batchSession.getMapper(ReferenceMapper.class);
    tm = batchSession.getMapper(TaxonMapper.class);
    nm = batchSession.getMapper(NameMapper.class);
    // for reading only
    num = session.getMapper(NameUsageMapper.class);
    // load target taxon
    Taxon t = tm.get(sector.getTargetAsDSID());
    target = new Usage(t.getId(), t.getName().getRank(), t.getStatus());
  }

  public void reset() throws InterruptedException {
    processLast();
    ids.clear();
  }

  public Map<String, Usage> getUsageIds() {
    return ids;
  }

  public Map<String, String> getRefIds() {
    return refIds;
  }

  public Map<String, String> getNameIds() {
    return nameIds;
  }

  /**
   * Copies all name and taxon relations based on ids collected during the accept calls by the tree traversal.
   */
  void copyRelations() {
    // copy name relations
    copyNameRelations();

    // copy taxon relations
    copyTaxonRelations();
  }

  private void copyTaxonRelations() {
    // TODO: copy taxon relations
    LOG.info("Synced {} taxon relations from sector {} - NOT IMPLEMENTED", 0, sector.getKey());
  }

  private void copyNameRelations(){
    // copy name relations
    NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
    NameRelationMapper nrmWrite = batchSession.getMapper(NameRelationMapper.class);
    int counter = 0;
    IntSet relIds = new IntOpenHashSet();

    var key = DSID.of(sector.getSubjectDatasetKey(), "");
    for (Map.Entry<String, String> n : nameIds.entrySet()) {
      for (NameRelation nr : nrm.listByName(key.id(n.getKey()))) {
        if (!relIds.contains((int)nr.getId())) {
          updateFKs(nr);
          nr.setNameId(nameIds.get(nr.getNameId()));
          nr.setRelatedNameId(nameIds.get(nr.getRelatedNameId()));
          if (nr.getNameId() != null && nr.getRelatedNameId() != null) {
            nrmWrite.create(nr);
            relIds.add((int)nr.getId());
            if (counter++ % 2500 == 0) {
              batchSession.commit();
            }
          } else {
            LOG.info("Name relation {} outside of synced sector {}", nr.getKey(), sector.getKey());
          }
        }
      }
    }
    batchSession.commit();
    LOG.info("Synced {} name relations from sector {}", relIds.size(), sector.getKey());
  }

  private void updateFKs(DatasetScopedEntity<?> obj){
    obj.setDatasetKey(sector.getDatasetKey());
    if (obj instanceof VerbatimEntity) {
      ((VerbatimEntity)obj).setVerbatimKey(null);
    }
    if (obj instanceof Referenced) {
      Referenced r = (Referenced) obj;
      r.setReferenceId(lookupReference(r.getReferenceId()));
    }
  }

  static class Usage {
    String id;
    Rank rank;
    TaxonomicStatus status;
    
    Usage(String id, Rank rank, TaxonomicStatus status) {
      this.id = id;
      this.rank = rank;
      this.status = status;
    }
    Usage(SimpleName sn) {
      this(sn.getId(), sn.getRank(), sn.getStatus());
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

  /**
   * If needed creates missing implicit taxa for species or genera.
   * Implicit names are not created for:
   *  - unparsed names
   *  - provisional names
   *  - indetermined names, i.e. a species with no specific epithet given
   *
   * @return the parent, either as supplied or the new one if implicit taxa were created
   */
  private Usage createImplicit(Usage parent, Taxon taxon) {
    List<Rank> neededRanks = new ArrayList<>();
    
    // figure out if we need to create any implicit taxon
    Name origName = taxon.getName();
    // do only create implicit names if the name is parsed & not provisional
    // see https://github.com/CatalogueOfLife/coldp/issues/45
    if (origName.isParsed() && !origName.isIndetermined() && !taxon.isProvisional()) {
      for (Rank r : implicitRanks) {
        if (parent.rank.higherThan(r) && r.higherThan(origName.getRank())) {
          neededRanks.add(r);
        }
      }
      // now see if we have copied such a name already - avoid creating duplicates: https://github.com/CatalogueOfLife/testing/issues/189
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
        // did we sync the name before in the same sector?
        var existing = findInSector(rnn);
        if (existing != null) {
          LOG.debug("Found implicit {} {} in sector {}", r, origName.getScientificName(), sector);
          parent = existing;
          implicits.put(rnn, existing);
          continue;
        }
        // finally, create missing implicit name
        DatasetEntityDao.newKey(n);
        n.setDatasetKey(catalogueKey);
        n.setOrigin(Origin.IMPLICIT_NAME);
        n.applyUser(user);
        LOG.debug("Create implicit {} from {}: {}", r, origName.getScientificName(), n);
        nm.create(n);
        // match name
        createMatch(n);

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

  private Usage findInSector(RanKnName rnn) {
    // we need to commit the batch session to see the recent inserts
    batchSession.commit();
    var matches = num.findSimple(catalogueKey, sector.getKey().getId(), TaxonomicStatus.ACCEPTED, rnn.rank, rnn.name);
    if (!matches.isEmpty()) {
      return new Usage(matches.get(0));
    }
    return null;
  }

  @Override
  public void acceptThrows(NameUsageBase u) throws InterruptedException {
    // We buffer all children of a given parentID and sort them by rank before we pass them on to the handler
    if (!Objects.equals(lastParentID, u.getParentId())) {
      processLast();
      lastParentID = u.getParentId();
    }
    lastUsages.add(u);
  }

  private void processLast() throws InterruptedException {
    // sort by rank, then process
    lastUsages.sort(Comparator.comparing(NameUsage::getRank));
    for (var u : lastUsages) {
      process(u);
    }
    lastUsages.clear();
  }

  private void process(NameUsageBase u) throws InterruptedException {
    u.setSectorKey(sector.getId());
    u.getName().setSectorKey(sector.getId());
    // before we apply a specific decision
    if (sector.getCode() != null) {
      u.getName().setCode(sector.getCode());
    }
    
    if (decisions.containsKey(u.getId())) {
      applyDecision(u, decisions.get(u.getId()));
    }
    if (skipUsage(u, decisions.get(u.getId()))) {
      // skip this taxon, but include children
      LOG.info("Ignore {} {} [{}] type={}; status={}", u.getName().getRank(), u.getName().getLabel(), u.getId(), u.getName().getType(), u.getName().getNomStatus());
      // use taxons parent also as the parentID for this so children link one level up
      ids.put(u.getId(), ids.getOrDefault(u.getParentId(), target));
      return;
    }
    // all non root nodes have newly created parents
    Usage parent = ids.getOrDefault(u.getParentId(), target);
    // apply general COL rules
    CoLUsageRules.apply(u, parent);

    // make sure we have a genus for species and a species for infraspecific taxa
    if (u.isTaxon() && u.getName().getRank().isSpeciesOrBelow()) {
      parent = createImplicit(parent, (Taxon) u);
    }

    // copy usage with all associated information. This assigns a new id !!!
    DSID<String> parentDID = new DSIDValue<>(catalogueKey, parent.id);
    String origNameID= u.getName().getId();
    DSID<String> orig = CatCopy.copyUsage(batchSession, u, parentDID, user.getKey(), entities, this::lookupReference, this::lookupReference);
    // remember old to new id mappings
    ids.put(orig.getId(), usage(u));
    nameIds.put(origNameID, u.getName().getId());
    // track source
    VerbatimSource v = new VerbatimSource(catalogueKey, u.getId(), sector.getSubjectDatasetKey(), orig.getId());
    vm.create(v);
    // match name
    createMatch(u.getName());

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

  private void incIgnored(IgnoreReason reason) {
    ignoredCounter.compute(reason, (k, v) -> v == null ? 1 : v+1);
  }

  private boolean skipUsage(NameUsageBase u, @Nullable EditorialDecision decision) {
    if (decision != null && decision.getMode() == EditorialDecision.Mode.IGNORE) {
      return true;
    }
    Name n = u.getName();
    if (u.isTaxon()) {
      // apply rank filter only for accepted names, always allow any synonyms
      if (!ranks.isEmpty() && !ranks.contains(n.getRank())) {
        incIgnored(IgnoreReason.RANK);
        return true;
      }
    }
    switch (n.getType()) {
      case PLACEHOLDER:
      case NO_NAME:
      case HYBRID_FORMULA:
      case INFORMAL:
        incIgnored(IgnoreReason.reasonByNameType(n.getType()));
        return true;
    }
    if (n.getNomStatus() != null && n.getNomStatus() == NomStatus.CHRESONYM) {
      incIgnored(IgnoreReason.CHRESONYM);
      return true;
    }
    if (n.getCultivarEpithet() != null || n.getCode() == NomCode.CULTIVARS || n.getRank().isCultivarRank()) {
      incIgnored(IgnoreReason.INCONSISTENT_NAME);
      return true;
    }
    if (n.getType().isParsable() && n.isIndetermined()) {
      incIgnored(IgnoreReason.INDETERMINED);
      return true;
    }
    return false;
  }
  
  private void applyDecision(NameUsageBase u, EditorialDecision ed) throws InterruptedException {
    switch (ed.getMode()) {
      case BLOCK:
        throw new IllegalStateException("Blocked usage " + u.getId() + " should not have been traversed");
      case UPDATE:
        decisionCounter++;
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
          if (n2.getRank() != null) {
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
          if (ed.getEnvironments() != null) {
            t.setEnvironments(ed.getEnvironments());
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

  private void createMatch(Name n) {
    NameMatch m = nameIndex.match(n, true, false);
    if (m.hasMatch()) {
      session.getMapper(NameMatchMapper.class).create(n, n.getSectorKey(), m.getNameKey(), m.getType());
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
    try {
      processLast();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();

    } finally {
      session.commit();
      session.close();
      batchSession.commit();
      batchSession.close();
    }
  }

}
