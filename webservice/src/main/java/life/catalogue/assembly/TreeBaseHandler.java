package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.dao.CopyUtil;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.NameIndex;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.*;

import java.util.*;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import static life.catalogue.api.util.ObjectUtils.coalesce;
import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

public abstract class TreeBaseHandler implements TreeHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeBaseHandler.class);
  protected final Set<EntityType> entities;
  protected final Set<Rank> ranks;
  protected static List<Rank> IMPLICITS = ImmutableList.of(Rank.GENUS, Rank.SUBGENUS, Rank.SPECIES);
  protected final List<Rank> implicitRanks = new ArrayList<>();

  protected final int targetDatasetKey;
  protected final DSID<String> targetKey; // key to some target usage that can be reused
  protected final User user;
  protected final Sector sector;
  protected final Dataset source;
  protected final SectorImport state;
  protected final Map<String, EditorialDecision> decisions;
  protected final NameIndex nameIndex;
  protected final SqlSession session;
  protected final SqlSession batchSession;
  protected final VerbatimSourceMapper vsm;
  protected final NameMatchMapper nmm;
  protected final ReferenceMapper rm;
  protected final TaxonMapper tm;
  protected final NameMapper nm;
  // for reading only:
  protected final NameUsageMapper num;
  protected final VerbatimRecordMapper vrm;
  protected final Usage targetUsage;
  protected final Taxon target;
  // tracker
  protected final Set<String> ignoredTaxa = new HashSet<>(); // usageIDs of skipped accepted names only
  // id generators
  protected final Supplier<String> nameIdGen;
  protected final Supplier<String> usageIdGen;
  protected final Supplier<String> typeMaterialIdGen;
  // counter
  protected final Map<IgnoreReason, Integer> ignoredCounter = new EnumMap<>(IgnoreReason.class);
  protected final Map<NomRelType, Map<String, String>> nameRelsToBeCreated = new HashMap<>();
  private final Map<String, String> refIds = new HashMap<>();
  // from name to related name with given type

  protected int sCounter = 0;
  protected int tCounter = 0;
  protected int decisionCounter = 0;

  public TreeBaseHandler(int targetDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex,
                         User user, Sector sector, SectorImport state,
                         Supplier<String> nameIdGen, Supplier<String> usageIdGen, Supplier<String> typeMaterialIdGen) {
    this.targetDatasetKey = targetDatasetKey;
    this.targetKey = DSID.root(targetDatasetKey);
    this.user = user;
    this.sector = Preconditions.checkNotNull(sector, "Sector required");
    this.state = state;
    this.decisions = decisions;
    this.nameIndex = nameIndex;
    this.nameIdGen = nameIdGen;
    this.usageIdGen = usageIdGen;
    this.typeMaterialIdGen = typeMaterialIdGen;
    this.entities = Preconditions.checkNotNull(sector.getEntities(), "Sector entities required");
    LOG.info("Include taxon extensions: {}", Joiner.on(", ").join(entities));

    if (sector.getNameTypes() != null && !sector.getNameTypes().isEmpty())  {
      LOG.info("Include only name types: {}", Joiner.on(", ").join(sector.getNameTypes()));
    }

    if (sector.getNameStatusExclusion() != null && !sector.getNameStatusExclusion().isEmpty())  {
      LOG.info("Include only name status: {}", Joiner.on(", ").join(sector.getNameStatusExclusion()));
    }

    this.ranks = Preconditions.checkNotNull(sector.getRanks(), "Sector ranks required");
    if (ranks.size() < Rank.values().length) {
      LOG.info("Consider only ranks: {}", Joiner.on(", ").join(ranks));
    }

    for (Rank r : IMPLICITS) {
      if (!ranks.isEmpty() && ranks.contains(r)) {
        implicitRanks.add(r);
      }
    }
    LOG.info("Create implicit taxa for ranks {}", Joiner.on(", ").join(implicitRanks));

    // we open up a separate batch session that we can write to so we do not disturb the open main cursor for processing with this handler
    session = factory.openSession(true);
    num = session.getMapper(NameUsageMapper.class);
    vrm = session.getMapper(VerbatimRecordMapper.class);
    // load target taxon
    target = session.getMapper(TaxonMapper.class).get(sector.getTargetAsDSID());
    targetUsage = usage(target);
    // load source dataset
    source = session.getMapper(DatasetMapper.class).get(sector.getSubjectDatasetKey());

    // writes only
    batchSession = factory.openSession(ExecutorType.BATCH, false);
    vsm = batchSession.getMapper(VerbatimSourceMapper.class);
    rm = batchSession.getMapper(ReferenceMapper.class);
    tm = batchSession.getMapper(TaxonMapper.class);
    nm = batchSession.getMapper(NameMapper.class);
    nmm= batchSession.getMapper(NameMatchMapper.class);
  }

  protected ModifiedUsage processCommon(NameUsageBase nu) {
    ModifiedUsage mod;
    // make rank non null
    if (nu.getName().getRank() == null) nu.getName().setRank(Rank.UNRANKED);
    // sector defaults before we apply a specific decision
    if (sector.getCode() != null) {
      nu.getName().setCode(sector.getCode());
    }
    // remove accordingTo?
    if (!sector.isCopyAccordingTo()) {
      nu.setAccordingToId(null);
    }
    if (sector.isRemoveOrdinals() && nu.isTaxon()) {
      ((Taxon)nu).setOrdinal(null);
    }
    // decisions
    if (decisions.containsKey(nu.getId())) {
      mod = applyDecision(nu, decisions.get(nu.getId()));
      nu = mod.usage;
    } else {
      // apply general rules otherwise
      SyncNameUsageRules.applyAlways(nu);
      mod = new ModifiedUsage(nu, false, false, null);
    }
    // match to nidx if no match result exists (NONE matches are fine, but not null)
    if (nu.getName().getNamesIndexType() == null) {
      var match = nameIndex.match(nu.getName(), true, false);
      nu.getName().applyMatch(match);
    }
    return mod;
  }

  protected void processEnd(@Nullable SimpleName sn, ModifiedUsage mod) throws InterruptedException {
    // create orth var name relation for synonyms
    if (sn != null
      && sn.isSynonym()
      && mod.createOrthVarRel
      && Boolean.TRUE.equals(mod.usage.getName().isOriginalSpelling())) {
      // find name id of accepted parent, the current name!
      var nid = nm.getNameIdByUsage(targetDatasetKey, sn.getParentId());
      nameRelsToBeCreated.computeIfAbsent(NomRelType.SPELLING_CORRECTION, k -> new HashMap<>())
                         .put(nid, mod.usage.getName().getId());
    }

    // in case of updates from decisions, track also the original name as a synonym?
    if (sn != null && !sn.isSynonym() && mod.keepOriginal && mod.originalName != null) {
      var origAsSyn = new Synonym(mod.originalName);
      origAsSyn.setId(mod.usage.getId());
      origAsSyn.setRemarks("Original spelling before change by an editorial decision");
      create(origAsSyn, new Usage(sn));
    }
    // commit in batches
    if (sCounter + tCounter > 0 && (sCounter + tCounter) % 1000 == 0) {
      interruptIfCancelled();
      session.commit();
      batchSession.commit();
    }
  }

  protected Usage usage(NameUsageBase u) {
    return u == null ? null : new Usage(u.getId(), u.getName().getRank(), u.getStatus());
  }

  protected Usage usage(SimpleName sn) {
    return sn == null ? null : new Usage(sn.getId(), sn.getRank(), sn.getStatus());
  }

  /**
   * Creates a new usage with the lowest current matched parent.
   * Updates the parent stack with the newly created taxon as the current parent match.
   * Increases stat counters.
   * @return the simple name instance of the newly created & matched usage with parent being the parentID
   */
  protected SimpleNameWithNidx create(NameUsageBase u, @Nullable Usage parent, Issue... issues) {
    if (parent == null) {
      if (u.isSynonym()) {
        throw new IllegalStateException("Cannot create synonym without a parent: " + u.getRank() +" "+ u.getLabel());
      } else {
        LOG.warn("Creating new root usage with no parent: {} {}", u.getRank(), u.getLabel());
      }
    }
    final String origID = u.getId();
    u.setSectorKey(sector.getId());
    u.getName().setSectorKey(sector.getId());

    // make sure we have a genus for species and a species for infraspecific taxa
    if (u.isTaxon() && u.getName().getRank().isSpeciesOrBelow()) {
      // remove infrageneric name from accepted bi/trinomials if the sector does not support subgenera
      if (u.getName().getInfragenericEpithet() != null && !ranks.contains(Rank.SUBGENUS)) {
        // do not create new subgenera, but attach to them if they already exist!
        if (parent != null && parent.rank.higherThan(Rank.SUBGENUS)) {
          var subgen = findSubgenus(u.getName().getCode(), u.getName().getGenus(), u.getName().getInfragenericEpithet(), parent);
          if (subgen != null) {
            parent = subgen;
          }
        }
        // remove it from the name to be created
        u.getName().setInfragenericEpithet(null);
        u.getName().rebuildScientificName();
      }
      parent = createImplicit(parent, (Taxon) u);
    }

    // copy usage with all associated information. This assigns a new id !!!
    CopyUtil.copyUsage(batchSession, u, targetKey.id(idOrNull(parent)), user.getKey(), entities,
      usageIdGen, nameIdGen, typeMaterialIdGen,
      this::lookupReference, this::lookupReference
    );
    // track source
    VerbatimSource v = new VerbatimSource(targetDatasetKey, u.getId(), sector.getSubjectDatasetKey(), origID);
    v.addIssues(issues);
    vsm.create(v);
    // match name
    var nm = matchName(u.getName());
    persistMatch(u.getName());
    LOG.debug("Created {} {} usage {} from source {}:{}", u.getStatus(), u.getRank(), u.getLabel(), sector.getSubjectDatasetKey(), u.getId());

    if (u.isTaxon()) {
      state.setTaxonCount(++tCounter);
    } else {
      state.setSynonymCount(++sCounter);
    }

    return new SimpleNameWithNidx(u, nm.getCanonicalNameKey());
  }

  static String idOrNull(Usage u) {
    return u == null ? null : u.id;
  }

  /**
   * If needed creates missing implicit taxa for species or genera.
   * Implicit names are not created for:
   * - unparsed names
   * - provisional names
   * - indetermined names, i.e. a species with no specific epithet given
   *
   * @return the parent, either as supplied or the new one if implicit taxa were created
   */
  protected Usage createImplicit(@Nullable Usage parent, Taxon taxon) {
    // figure out if we need to create any implicit taxon
    Name origName = taxon.getName();
    // do only create implicit names if the name is parsed & not provisional
    // see https://github.com/CatalogueOfLife/coldp/issues/45
    if (origName.isParsed() && !origName.isIndetermined() && !taxon.isProvisional()) {
      List<Rank> neededRanks = new ArrayList<>();
      for (Rank r : implicitRanks) {
        if (parent == null || parent.rank.higherThan(r) && r.higherThan(origName.getRank())) {
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
          n.setGenus(origName.getGenus()); // we keep the genus placement
          n.setInfragenericEpithet(origName.getInfragenericEpithet());

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
        // match to names index - does not persist yet
        matchName(n);
        // did we sync the name before in the same sector?
        Usage existing = findExisting(n, parent);
        if (existing != null) {
          LOG.debug("Found implicit {} {} [{}] in project", r, n.getLabel(), existing.id);
          if (existing.status.isSynonym()) {
            // use accepted instead
            var acc = getSimpleParent(existing.id);
            if (acc == null) {
              LOG.warn("Could not use synonym {} {} as implicit name, the accepted name is missing!", r, n.getLabel());
              continue;
            }
            LOG.debug("Implicit {} {} is a synonym, use accepted name {} as parent and mark new name {} as provisional", r, n.getLabel(), acc, origName);
            parent = usage(acc);
            taxon.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
          } else {
            parent = existing;
          }
          continue;
        }
        // finally, create missing implicit name
        DatasetEntityDao.newKey(n);
        n.setDatasetKey(targetDatasetKey);
        n.setOrigin(Origin.IMPLICIT_NAME);
        n.applyUser(user);
        LOG.debug("Create implicit {} {} from {}", r, n.getLabel(), origName.getScientificName());
        nm.create(n);
        // persist match name
        persistMatch(n);

        Taxon t = new Taxon();
        DatasetEntityDao.newKey(t);
        t.setDatasetKey(targetDatasetKey);
        t.setName(n);
        if (parent != null) {
          t.setParentId(parent.id);
        }
        t.setSectorKey(sector.getId());
        t.setOrigin(Origin.IMPLICIT_NAME);
        t.setStatus(TaxonomicStatus.ACCEPTED);
        t.applyUser(user);
        tm.create(t);

        parent = usage(t);
        // allow reuse of implicit names
        cacheImplicit(t, parent);
      }
    }
    return parent;
  }

  protected SimpleName getSimpleParent(String id) {
    var p = num.getSimpleParent(targetKey.id(id));
    if (p == null) {
      batchSession.commit();
      p = num.getSimpleParent(targetKey.id(id));
      if (p == null) {
        LOG.warn("Parent not found for {}", target);
      }
    }
    return p;
  }

  protected Usage findUninomial(NomCode code, Rank rank, String uninomial, Usage parent) {
    Preconditions.checkArgument(rank.isGenusOrSuprageneric());
    Name n = new Name();
    n.setCode(code);
    n.setUninomial(uninomial);
    n.setRank(rank);
    n.setType(NameType.SCIENTIFIC);
    n.setSectorKey(sector.getId());
    n.rebuildScientificName();
    return findExisting(n, parent);
  }

  protected Usage findSubgenus(NomCode code, String genus, String subgenus, Usage parent) {
    Name n = new Name();
    n.setCode(code);
    n.setGenus(genus);
    n.setInfragenericEpithet(subgenus);
    n.setRank(Rank.SUBGENUS);
    n.setType(NameType.SCIENTIFIC);
    n.setSectorKey(sector.getId());
    n.rebuildScientificName();
    return findExisting(n, parent);
  }

  protected abstract Usage findExisting(Name n, Usage parent);

  protected abstract void cacheImplicit(Taxon t, Usage parent);

  /**
   * Matches the name, but does not store anything yet.
   */
  protected NameMatch matchName(Name n) {
    NameMatch m = nameIndex.match(n, true, false);
    n.setNamesIndexType(m.getType());
    n.setNamesIndexId(m.getNameKey());
    return m;
  }

  protected void persistMatch(Name n) {
    batchSession.getMapper(NameMatchMapper.class).create(n, n.getSectorKey(), n.getNamesIndexId(), n.getNamesIndexType());
  }

  protected boolean ignoreUsage(NameUsageBase u, @Nullable EditorialDecision decision) {
    // we must ignore synonyms for taxa which have been skipped
    if (u.isSynonym() && ignoredTaxa.contains(u.getParentId())) {
      // https://github.com/CatalogueOfLife/backend/issues/1150
      return incIgnored(IgnoreReason.IGNORED_PARENT, u);
    }

    if (decision != null) {
      // a reviewed decision actively overrides any other algorithmic decision below to exclude the usage
      if (decision.getMode() == EditorialDecision.Mode.REVIEWED) {
        LOG.info("Include {} {} [{}] because editorial reviewed decision", u.getName().getRank(), u.getName().getLabel(), u.getId());
        return false;
      }
      if (decision.getMode() == EditorialDecision.Mode.IGNORE) {
        LOG.info("Ignore {} {} [{}] because editorial ignore decision", u.getName().getRank(), u.getName().getLabel(), u.getId());
        return true;
      }
    }

    Name n = u.getName();
    if (u.isTaxon()) {
      // apply rank filter only for accepted names, always allow any synonyms
      if (!ranks.isEmpty() && !ranks.contains(n.getRank())) {
        return incIgnored(IgnoreReason.RANK, u);
      }
    }
    // apply name type filter if exists
    if (sector.getNameTypes() != null && !sector.getNameTypes().isEmpty() && !sector.getNameTypes().contains(n.getType())) {
      return incIgnored(IgnoreReason.reasonByNameType(n.getType()), u);
    }
    // apply name status filter if exists
    if (n.getNomStatus() != null && sector.getNameStatusExclusion() != null && sector.getNameStatusExclusion().contains(n.getNomStatus())) {
      return incIgnored(IgnoreReason.NOMENCLATURAL_STATUS, u);
    }

    if (n.getCultivarEpithet() != null || n.getCode() == NomCode.CULTIVARS || n.getRank().isCultivarRank()) {
      return incIgnored(IgnoreReason.INCONSISTENT_NAME, u);
    }
    if (n.getType().isParsable() && n.isIndetermined()) {
      return incIgnored(IgnoreReason.INDETERMINED, u);
    }

    return false;
  }

  public static class ModifiedUsage {
    final NameUsageBase usage;
    boolean createOrthVarRel = true;
    final boolean relink;
    final boolean keepOriginal;
    final Name originalName;

    ModifiedUsage(NameUsageBase usage, boolean relink, boolean keepOriginal, Name originalName) {
      this.usage = usage;
      this.relink = relink;
      this.keepOriginal = keepOriginal;
      this.originalName = originalName;
    }
  }

  /**
   * Applies the decision to the name usage, potentially modifying the original usage instance.
   */
  protected ModifiedUsage applyDecision(NameUsageBase u, EditorialDecision ed) {
    boolean linkUp = false;
    Name originalName = null;
    try {
      switch (ed.getMode()) {
        case BLOCK:
          throw new IllegalStateException("Blocked usage " +u.getLabel() + " [" + u.getId() + "] should not have been traversed");
        case UPDATE:
          decisionCounter++;
          originalName = new Name(u.getName());
          if (ed.getName() != null) {
            Name n = u.getName();
            Name n2 = ed.getName();

            if (n2.getScientificName() != null) {
              // parse a new name!
              final String name = n2.getScientificName() + " " + coalesce(n2.getAuthorship(), "");
              NomCode code = coalesce(n2.getCode(), n.getCode());
              Rank rank = coalesce(n2.getRank(), n.getRank());
              ParsedNameUsage nat = NameParser.PARSER.parse(name, rank, code, VerbatimRecord.VOID).orElseGet(() -> {
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
            }

            if (n2.getAuthorship() != null) {
              // just change the authorship, even if it was included in the name already
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
            // update name match
            var match = nameIndex.match(n, true, false);
            n.applyMatch(match);
          }
          // this can change a synonym to a taxon, so do it *before* we apply update values only relevant to taxa
          if (ed.getStatus() != null) {
            try {
              // change a synonym to a Taxon? needs an instance change...
              if (u.isSynonym() && ed.getStatus().isTaxon()) {
                u = new Taxon(u);
                linkUp = true;
              }
              u.setStatus(ed.getStatus());
            } catch (IllegalArgumentException e) {
              LOG.warn("Cannot convert {} {} from {} to {}: {}", u.getName().getRank(), u.getName().getLabel(), u.getStatus(), ed.getStatus(), e.getMessage());
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();  // set interrupt flag back
      throw new InterruptedRuntimeException(e);
    }
    return new ModifiedUsage(u, linkUp,
      // https://github.com/CatalogueOfLife/backend/issues/1292
      ed.getMode()== EditorialDecision.Mode.UPDATE && Boolean.TRUE.equals(ed.isKeepOriginalName()),
      originalName
    );
  }

  @Override
  public void copyRelations() {
    batchSession.commit();
    NameRelationMapper nrmWrite = batchSession.getMapper(NameRelationMapper.class);
    for (var type : nameRelsToBeCreated.entrySet()) {
      int counter = 0;
      for (var rel : type.getValue().entrySet()) {
        var nr = new NameRelation();
        nr.setDatasetKey(targetDatasetKey);
        nr.setSectorKey(sector.getId());
        nr.setType(type.getKey());
        nr.setNameId(rel.getKey());
        nr.setRelatedNameId(rel.getValue());
        nr.applyUser(Users.RELEASER);
        nrmWrite.create(nr);
        if (counter++ % 2500 == 0) {
          batchSession.commit();
        }
      }
      batchSession.commit();
      LOG.info("Created {} implicit {} name relations for sector {}", counter, type.getKey(), sector.getKey());
    }
  }

  protected boolean incIgnored(IgnoreReason reason, NameUsageBase u) {
    Object value = null;
    if (reason.getValueExtractor() != null) {
      value = reason.getValueExtractor().apply(u);
    }
    LOG.info("Ignore {} {} [{}] because {}{}", u.getName().getRank(), u.getName().getLabel(), u.getId(), reason, value == null ? "" : ": " + value);
    ignoredCounter.compute(reason, (k, v) -> v == null ? 1 : v+1);
    if (u.isTaxon()) {
      ignoredTaxa.add(u.getId());
    }
    return true;
  }

  protected String lookupReference(String refID) {
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

  protected String lookupReference(Reference ref) {
    if (ref != null) {
      if (refIds.containsKey(ref.getId())) {
        // we have seen this ref before
        return refIds.get(ref.getId());
      }
      // sth new?
      List<Reference> matches = rm.find(targetDatasetKey, sector.getId(), ref.getCitation());
      if (matches.isEmpty()) {
        // insert new ref
        ref.setDatasetKey(targetDatasetKey);
        ref.setSectorKey(sector.getId());
        ref.applyUser(user);
        DSID<String> origID = ReferenceDao.copyReference(batchSession, ref, targetDatasetKey, user.getKey());
        refIds.put(origID.getId(), ref.getId());
        return ref.getId();

      } else {
        if (matches.size() > 1) {
          LOG.warn("{} duplicate references in catalogue {} with citation {}", matches.size(), targetDatasetKey, ref.getCitation());
        }
        String refID = matches.get(0).getId();
        refIds.put(ref.getId(), refID);
        return refID;
      }
    }
    return null;
  }

  @Override
  public RuntimeException wrapException(Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();  // set interrupt flag back
      return new InterruptedRuntimeException(e);
    }
    return new RuntimeException(e);
  }

  @Override
  public void reset() throws InterruptedException {
    ignoredTaxa.clear();
  }

  @Override
  public void close() throws InterruptedException{
    session.commit();
    session.close();
    batchSession.commit();
    batchSession.close();
  }
}
