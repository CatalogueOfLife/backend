package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.dao.CatCopy;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.NameIndex;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.*;

import java.util.*;

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

public abstract class TreeBaseHandler implements TreeHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeBaseHandler.class);
  protected final Set<EntityType> entities;
  protected final Set<Rank> ranks;
  protected static List<Rank> IMPLICITS = ImmutableList.of(Rank.GENUS, Rank.SUBGENUS, Rank.SPECIES);
  protected final List<Rank> implicitRanks = new ArrayList<>();

  protected final int targetDatasetKey;
  protected final User user;
  protected final Sector sector;
  protected final Dataset source;
  protected final SectorImport state;
  protected final Map<String, EditorialDecision> decisions;
  protected final NameIndex nameIndex;
  protected final SqlSession session;
  protected final SqlSession batchSession;
  protected final VerbatimSourceMapper vm;
  protected final ReferenceMapper rm;
  protected final TaxonMapper tm;
  protected final NameMapper nm;
  // for reading only:
  protected final NameUsageMapper num;
  protected final Usage targetUsage;
  protected final Taxon target;
  // tracker
  protected final Set<String> ignoredTaxa = new HashSet<>(); // usageIDs of skipped accepted names only
  // counter
  protected final Map<IgnoreReason, Integer> ignoredCounter = new HashMap<>();
  private final Map<String, String> refIds = new HashMap<>();
  protected int sCounter = 0;
  protected int tCounter = 0;
  protected int decisionCounter = 0;

  public TreeBaseHandler(Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, User user, Sector sector, SectorImport state) {
    this.targetDatasetKey = sector.getDatasetKey();
    this.user = user;
    this.sector = sector;
    this.state = state;
    this.decisions = decisions;
    this.nameIndex = nameIndex;

    this.entities = Preconditions.checkNotNull(sector.getEntities(), "Sector entities required");
    LOG.info("Process taxon extensions: {}", Joiner.on(", ").join(entities));

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
    // load target taxon
    target = session.getMapper(TaxonMapper.class).get(sector.getTargetAsDSID());
    targetUsage = usage(target);
    // load source dataset
    source = session.getMapper(DatasetMapper.class).get(sector.getSubjectDatasetKey());

    // writes only
    batchSession = factory.openSession(ExecutorType.BATCH, false);
    vm = batchSession.getMapper(VerbatimSourceMapper.class);
    rm = batchSession.getMapper(ReferenceMapper.class);
    tm = batchSession.getMapper(TaxonMapper.class);
    nm = batchSession.getMapper(NameMapper.class);
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
   * @return the original source taxon id
   */
  protected DSID<String> create(NameUsageBase u, Usage parent) {
    u.setSectorKey(sector.getId());
    u.getName().setSectorKey(sector.getId());

    // apply general COL rules
    CoLUsageRules.apply(u);

    // make sure we have a genus for species and a species for infraspecific taxa
    if (u.isTaxon() && u.getName().getRank().isSpeciesOrBelow()) {
      parent = createImplicit(parent, (Taxon) u);
    }

    // copy usage with all associated information. This assigns a new id !!!
    DSID<String> parentDID = new DSIDValue<>(targetDatasetKey, parent.id);
    DSID<String> orig = CatCopy.copyUsage(batchSession, u, parentDID, user.getKey(), entities, this::lookupReference, this::lookupReference);
    // track source
    VerbatimSource v = new VerbatimSource(targetDatasetKey, u.getId(), sector.getSubjectDatasetKey(), orig.getId());
    vm.create(v);
    // match name
    matchName(u.getName());
    persistMatch(u.getName());

    if (u.isTaxon()) {
      state.setTaxonCount(++tCounter);
    } else {
      state.setSynonymCount(++sCounter);
    }

    return orig;
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
  protected Usage createImplicit(Usage parent, Taxon taxon) {
    // figure out if we need to create any implicit taxon
    Name origName = taxon.getName();
    // do only create implicit names if the name is parsed & not provisional
    // see https://github.com/CatalogueOfLife/coldp/issues/45
    if (origName.isParsed() && !origName.isIndetermined() && !taxon.isProvisional()) {
      List<Rank> neededRanks = new ArrayList<>();
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
        // match to names index - does not persist yet
        matchName(n);
        // did we sync the name before in the same sector?
        Usage existing = findExisting(n);
        if (existing != null) {
          LOG.debug("Found implicit {} {} in sector {}", r, origName.getScientificName(), sector);
          parent = existing;
          continue;
        }
        // finally, create missing implicit name
        DatasetEntityDao.newKey(n);
        n.setDatasetKey(targetDatasetKey);
        n.setOrigin(Origin.IMPLICIT_NAME);
        n.applyUser(user);
        LOG.debug("Create implicit {} from {}: {}", r, origName.getScientificName(), n);
        nm.create(n);
        // persist match name
        persistMatch(n);

        Taxon t = new Taxon();
        DatasetEntityDao.newKey(t);
        t.setDatasetKey(targetDatasetKey);
        t.setName(n);
        t.setParentId(parent.id);
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

  protected abstract Usage findExisting(Name n);

  protected abstract void cacheImplicit(Taxon t, Usage parent);

  /**
   * Matches the name, but does not store anything yet.
   */
  protected void matchName(Name n) {
    NameMatch m = nameIndex.match(n, true, false);
    if (m.hasMatch()) {
      n.setNamesIndexType(m.getType());
      n.setNamesIndexId(m.getNameKey());
    } else {
      n.setNamesIndexType(MatchType.NONE);
    }
  }

  protected void persistMatch(Name n) {
    if (n.getNamesIndexId() != null) {
      session.getMapper(NameMatchMapper.class).create(n, n.getSectorKey(), n.getNamesIndexId(), n.getNamesIndexType());
    }
  }

  protected boolean ignoreUsage(NameUsageBase u, @Nullable EditorialDecision decision, UsageMatch match) {
    if (decision != null && decision.getMode() == EditorialDecision.Mode.IGNORE) {
      return true;
    }
    if (match.ignore) {
      return true;
    }

    Name n = u.getName();
    //TODO: make this configurable, allow in merge handler
    if (u.isTaxon()) {
      // apply rank filter only for accepted names, always allow any synonyms
      if (!ranks.isEmpty() && !ranks.contains(n.getRank())) {
        incIgnored(IgnoreReason.RANK);
        return true;
      }
    } else if (ignoredTaxa.contains(u.getParentId())) {
      // we ignore synonyms for taxa which have been skipped
      // https://github.com/CatalogueOfLife/backend/issues/1150
      incIgnored(IgnoreReason.IGNORED_PARENT);
      return true;
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

  protected void applyDecision(NameUsageBase u, EditorialDecision ed) {
    try {
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();  // set interrupt flag back
      throw new InterruptedRuntimeException(e);
    }
  }

  protected void incIgnored(IgnoreReason reason) {
    ignoredCounter.compute(reason, (k, v) -> v == null ? 1 : v+1);
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
  public void reset() {
    ignoredTaxa.clear();
  }

  @Override
  public void close() {
    session.commit();
    session.close();
    batchSession.commit();
    batchSession.close();
  }
}
