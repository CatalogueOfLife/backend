package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.dao.CopyUtil;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TypeMaterialMapper;
import life.catalogue.db.mapper.VernacularNameMapper;
import life.catalogue.matching.*;
import life.catalogue.matching.nidx.NameIndex;

import life.catalogue.release.UsageIdGen;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static life.catalogue.common.text.StringUtils.removeWhitespace;
/**
 * Expects depth first traversal!
 */
public class TreeMergeHandler extends TreeBaseHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeMergeHandler.class);
  public static final char ID_PREFIX = '~';
  private final MatchedParentStack parents;
  private final UsageMatcherGlobal matcher;
  private final UsageCache uCache;
  private final CacheLoader loader;
  private int counter = 0;  // all source usages
  private int ignored = 0;
  private int thrown = 0;
  private int created = 0;
  private int updated = 0; // updates
  private final @Nullable TreeMergeHandlerConfig cfg;
  private final DSID<Integer> vKey;

  TreeMergeHandler(int targetDatasetKey, int sourceDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, UsageMatcherGlobal matcher,
                   User user, Sector sector, SectorImport state, @Nullable TreeMergeHandlerConfig cfg,
                   Supplier<String> nameIdGen, Supplier<String> typeMaterialIdGen, UsageIdGen usageIdGen) {
    // we use much smaller ids than UUID which are terribly long to iterate over the entire tree - which requires to build a path from all parent IDs
    // this causes postgres to use a lot of memory and creates very large temporary files
    super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state, nameIdGen, typeMaterialIdGen, usageIdGen);
    this.cfg = cfg;
    this.vKey = DSID.root(sourceDatasetKey);
    this.matcher = matcher;
    uCache = matcher.getUCache();

    // figure out the lowest insertion point in the project/release
    // a) a target is given
    // b) a subject is given. Match it and see if it is lower and inside the target
    // c) nothing, but there maybe is an incertae sedis taxon configured to collect all unplaced
    SimpleNameWithNidx trgt = null;
    if (target != null) {
      trgt = matcher.toSimpleName(target);
    } else if (cfg != null && cfg.incertae != null) {
      trgt = matcher.toSimpleName(cfg.incertae);
    }
    parents = new MatchedParentStack(trgt);
    if (sector.getSubject() != null) {
      // match subject and its classification
      try (SqlSession session = factory.openSession()) {
        var num = session.getMapper(NameUsageMapper.class);
        // loop over classification incl the subject itself as the last usage
        for (var p : num.getClassification(sector.getSubjectAsDSID())) {
          var nusn = matcher.toSimpleName(p);
          parents.push(nusn);
          UsageMatch match = matcher.matchWithParents(targetDatasetKey, p, parents.classification(), false, false);
          if (match.isMatch()) {
            parents.setMatch(match.usage);
          }
        }
        var lowest = parents.lowestParentMatch();
        if (lowest != null && (trgt == null || !lowest.getId().equals(trgt.getId()))) {
          // found a lower target than we had before!
          LOG.info("The sector subject {} resulted in a lower target match to use for merging: {}", sector.getSubject(), lowest);
          parents.setRoot(lowest);
        }
      }
    }
    this.loader = new CacheLoader.Mybatis(batchSession, true);
    matcher.registerLoader(targetDatasetKey, loader); // we need to make sure we remove it at the end no matter what!

    // check if requested entities are supported in the source at all
    try (SqlSession session = factory.openSession()) {
      if (entities.contains(EntityType.VERNACULAR)) {
        var mapper = session.getMapper(VernacularNameMapper.class);
        if (!mapper.entityExists(sourceDatasetKey)) {
          entities.remove(EntityType.VERNACULAR);
          LOG.info("No vernacular names in sector {}", sector);
        }
      }
      if (entities.contains(EntityType.TYPE_MATERIAL)) {
        var mapper = session.getMapper(TypeMaterialMapper.class);
        if (!mapper.entityExists(sourceDatasetKey)) {
          entities.remove(EntityType.TYPE_MATERIAL);
          LOG.info("No type material in sector {}", sector);
        }
      }
    }
  }


  @Override
  public void reset() {
    // only needed for UNION sectors which do several iterations
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasThrown() {
    return thrown > 0;
  }

  @Override
  public Map<IgnoreReason, Integer> getIgnoredCounter() {
    return ignoredCounter;
  }

  @Override
  public int getDecisionCounter() {
    return decisionCounter;
  }

  @Override
  public void acceptThrows(NameUsageBase nu) throws InterruptedException {
    try {
      acceptThrowsNoCatch(nu);
    } catch (InterruptedException e) {
      throw e; // rethrow real interruptions

    } catch (RuntimeException e) {
      LOG.error("Unable to process {} with parent {}. {}:{}", nu, nu.getParentId(), e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;

    } catch (Exception e) {
      LOG.error("Unable to process {} with parent {}. {}:{}", nu, nu.getParentId(), e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;
      throw new RuntimeException(e);
    }
  }

  public void acceptThrowsNoCatch(NameUsageBase nu) throws Exception {
    counter++;
    LOG.debug("process {} {} {} -> {}", nu.getStatus(), nu.getName().getRank(), nu.getLabel(), parents.classificationToString());

    // apply common changes to the usage
    var mod = processCommon(nu);

    // track parent classification and match to existing usages. Create new ones if they dont yet exist
    var nusn = matcher.toSimpleName(nu);
    parents.push(nusn);

    // ignore doubtfully marked usages in classification, e-g- wrong rank ordering
    if (parents.isDoubtful()) {
      ignored++;
      LOG.info("Ignore {} {} [{}] because it has a bad parent classification {}", nu.getName().getRank(), nu.getName().getLabel(), nu.getId(), parents.getDoubtful().usage);
      return;
    }

    // find out matching - even if we ignore the name in the merge we want the parents matched for classification comparisons
    // we have a custom usage loader registered that knows about the open batch session
    // that writes new usages to the release which might not be flushed to the database
    UsageMatch match = matcher.matchWithParents(targetDatasetKey, nu, parents.classification(), true, false);
    LOG.debug("{} matches {}", nu.getLabel(), match);

    // figure out closest matched parent that we can use to attach to
    Usage parent;
    if (nu.isSynonym()) {
      // make sure synonyms have a matched direct parent (second last, cause the last is the synonym itself)
      // parent can be null here, but we will skip synonyms that have no matched parent in ignoreUsage() below
      parent = usage(parents.secondLast().match);
    } else {
      // otherwise use the parent stacks lowest taxon or root, e.g. incertae sedis
      parent = usage(parents.lowestParentMatch());
    }

    // some sources contain the same name multiple times with different status. Good pro parte ones or bad ones...
    // we allow any number of synonyms as long as they have different parents
    // but only allow a single accepted name
    if (match.isMatch() && Objects.equals(sector.getId(), match.sectorKey) &&
        (match.usage.getStatus().isSynonym() || nu.getStatus().isSynonym())
    ) {
      // verify parents are different
      if (parent == null ||
          // different parents, but not the same as the match, we dont want synonyms that point to themselves as accepted
          (!parent.id.equals(match.usage.getParent()) && !parent.id.equals(match.usage.getId()))
      ) {
        LOG.debug("Ignore match to potential pro parte synonym complex from the same source: {}", match.usage.getLabel());
        match = UsageMatch.empty(targetDatasetKey);
        //TODO: reuse existing name instance for pro parte usages when they are created below
      }
    }

    // remember the match
    parents.setMatch(match.usage);

    // check if usage should be ignored AFTER matching as we need the parents matched to attach child taxa correctly
    if (match.ignore || ignoreUsage(nu, decisions.get(nu.getId()), true)) {
      // skip this taxon, but include children
      ignored++;
      return;
    }

    // finally create or update records
    SimpleNameWithNidx sn = null;
    if (match.isMatch()) {
      // *** UPDATE ***
      update(nu, match);
      sn = match.usage;
      mod.createOrthVarRel = false; // dont create new name relations for spelling corrections

    } else if (match.type == MatchType.AMBIGUOUS) {
      LOG.debug("Do not create new name as we had {} ambiguous matches for {}", match.alternatives.size(), nu.getLabel());

    } else {
      // replace accepted taxa with doubtful ones for all nomenclators and for genus parents which are synonyms
      // provisionally accepted species & infraspecies will not create an implicit genus or species !!!
      if (nu.getStatus() == TaxonomicStatus.ACCEPTED && (source.getType() == DatasetType.NOMENCLATURAL ||
        parent != null && parent.status.isSynonym() && parent.rank == Rank.GENUS)
      ) {
        nu.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
      }
      if (parent != null && parent.status.isSynonym()) {
        // use accepted instead
        var p = num.getSimpleParent(targetKey.id(parent.id));
        // make sure rank hierarchy makes sense - can be distorted by synonyms
        if (nu.getRank().notOtherOrUnranked() && p.getRank().lowerOrEqualsTo(nu.getRank())) {
          while (p != null && p.getRank().lowerOrEqualsTo(nu.getRank())) {
            p = num.getSimpleParent(targetKey.id(p.getId()));
          }
          if (p == null) {
            // nothing to attach to. Better skip this taxon, but include children
            LOG.debug("Ignore name which links to a synonym and for which we cannot find a suitable parent: {}", nu.getLabel());
            ignored++;
            return;
          }
        }
        parent = usage(p);
      }

      // only add a new name if we do not have already multiple names that we cannot clearly match
      // track if we are outside of the sector target
      Issue[] issues;
      if (sector.getTarget() != null && parent != null
        && !containsID(uCache.getClassification(targetKey.id(parent.id), loader), sector.getTarget().getId())) {
        issues = new Issue[]{Issue.SYNC_OUTSIDE_TARGET};
      } else {
        issues = new Issue[0];
      }
      // *** CREATE ***
      sn = create(nu, parent, issues);
      parents.setMatch(sn);
      matcher.add(nu);
      created++;
    }

    processEnd(sn, mod);
  }

  private static boolean containsID(List<SimpleNameCached> usages,  String id){
    return usages != null && usages.stream().anyMatch(u -> u.getId().equals(id));
  }

  @Override
  protected boolean ignoreUsage(NameUsageBase u, @Nullable EditorialDecision decision, boolean filterSynonymsByRank) {
    var ignore =  super.ignoreUsage(u, decision, true);
    if (!ignore) {
      // additional checks - we dont want any unranked unless they are OTU names
      ignore = u.getRank() == Rank.UNRANKED && u.getName().getType() != NameType.OTU
        || (cfg != null && cfg.isBlocked(u.getName()));
      // if issues are to be excluded we need to load the verbatim records
      if (cfg != null && !cfg.xCfg.issueExclusion.isEmpty() && u.getName().getVerbatimKey() != null) {
        var issues = vrm.getIssues(vKey.id(u.getName().getVerbatimKey()));
        if (issues != null && CollectionUtils.overlaps(issues.getIssues(), cfg.xCfg.issueExclusion)) {
          LOG.debug("Ignore {} because of excluded issues: {}", u.getLabel(), StringUtils.join(issues, ","));
          return true;
        }
      }
    }
    return ignore;
  }

  /**
   * Use the same usage matching to find existing taxa
   */
  @Override
  protected Usage findExisting(Name n, Usage parent) {
    Taxon t = new Taxon(n);
    var m = matcher.matchWithParents(targetDatasetKey, t, parents.classification(), true, false);
    // make sure rank is correct - canonical matches are across ranks
    if (m.usage != null && m.usage.getRank() == n.getRank()) {
      return usage(m.usage);
    }
    return null;
  }

  @Override
  protected void cacheImplicit(Taxon t, Usage parent) {
    matcher.add(t);
  }

  private Name loadFromDB(String usageID) {
    return nm.getByUsage(targetDatasetKey, usageID);
  }

  private boolean proposedParentDoesNotConflict(SimpleName existing, SimpleName existingParent, SimpleName proposedParent) {
    boolean existingParentFound = false;
    if (existingParent.getRank().higherThan(proposedParent.getRank())
           && !existingParent.getId().equals(proposedParent.getId())
    ) {
      // now check the newly proposed classification does also contain the current parent to avoid changes - we only want to patch missing ranks
      // but also make sure the existing name is not part of the proposed classification as this will result in a fatal circular loop!
      var proposedClassification = uCache.getClassification(proposedParent.toDSID(targetDatasetKey), loader);
      for (var propHigherTaxon : proposedClassification) {
        if (propHigherTaxon.getId().equals(existing.getId())) {
          LOG.debug("Avoid circular classifications by updating the parent of {} {} to {} {}", existing.getRank(), existing.getLabel(), proposedParent.getRank(), proposedParent.getLabel());
          return false;
        }
        if (propHigherTaxon.getId().equals(existingParent.getId())) {
          existingParentFound = true;
        }
      }
    }
    return existingParentFound;
  }

  private boolean update(NameUsageBase nu, UsageMatch existing) {
    if (nu.getStatus().getMajorStatus() == existing.usage.getStatus().getMajorStatus()) {
      LOG.debug("Update {} {} {} from source {}:{} with status {}", existing.usage.getStatus(), existing.usage.getRank(), existing.usage.getLabel(), sector.getSubjectDatasetKey(), nu.getId(), nu.getStatus());

      Set<InfoGroup> upd = EnumSet.noneOf(InfoGroup.class);
      // set targetKey to the existing usage
      final var existingUsageKey = DSID.of(targetDatasetKey, existing.usage.getId());
      // patch classification of accepted names if direct parent adds to it
      if (existing.usage.getStatus().isTaxon()) {
        var matchedParents = parents.matchedParentsOnly(existing.usage.getId());
        if (!matchedParents.isEmpty()) {
          var parent = matchedParents.getLast().match;
          if (parent != null) {
            if (parent.getStatus().isSynonym()) {
              LOG.info("Do not update {} with a closer synonym parent {} {} from {}", existing.usage, parent.getRank(), parent.getId(), nu);

            } else {
              var existingParent = existing.usage.getClassification() == null || existing.usage.getClassification().isEmpty() ? null : existing.usage.getClassification().get(0);
              batchSession.commit(); // we need to flush the write session to avoid broken foreign key constraints
              if (existingParent == null || proposedParentDoesNotConflict(existing.usage, existingParent, parent)) {
                LOG.debug("Update {} with closer parent {} {} than {} from {}", existing.usage, parent.getRank(), parent.getId(), existingParent, nu);
                num.updateParentId(existingUsageKey, parent.getId(), user.getKey());
                upd.add(InfoGroup.PARENT);
              }
            }
          }
        }
        // vernacular names
        if (entities.contains(EntityType.VERNACULAR)) {
          final var mapper = batchSession.getMapper(VernacularNameMapper.class);
          List<VernacularName> existingVNames = null;
          vnloop:
          for (var vn : mapper.listByTaxon(nu)) {
            // we only want to add vernaculars with a name & language
            if (vn.getName() == null || vn.getLanguage() == null) continue;

            // does it exist already?
            if (existingVNames == null) {
              // lazily query existing vnames
              existingVNames = mapper.listByTaxon(existing);
            }
            for (var evn : existingVNames) {
              if (sameName(vn, evn)) {
                continue vnloop;
              }
            }
            // a new vernacular
            vn.setId(null);
            vn.setVerbatimKey(null);
            vn.setSectorKey(sector.getId());
            vn.setDatasetKey(targetDatasetKey);
            vn.applyUser(user);
            // check if the entity refers to a reference which we need to lookup / copy
            String ridCopy = lookupReference(vn.getReferenceId());
            vn.setReferenceId(ridCopy);
            CopyUtil.transliterateVernacularName(vn, IssueContainer.VOID);
            mapper.create(vn, existingUsageKey.getId());
            existingVNames.add(vn);
          }
        }
      }

      // should we try to update the name? Need to load from db, so check upfront as much as possible to avoid db calls
      Name pn = null;
      if ((nu.getName().hasParsedAuthorship() && !existing.usage.hasAuthorship())
        || (!nu.getName().getRank().isUncomparable() && existing.usage.getRank().isUncomparable())
      ) {
        pn = loadFromDB(existing.usage.getId());

        if (nu.getName().hasParsedAuthorship() && !existing.usage.hasAuthorship()) {
          upd.add(InfoGroup.AUTHORSHIP);
          pn.setCombinationAuthorship(nu.getName().getCombinationAuthorship());
          pn.setSanctioningAuthor(nu.getName().getSanctioningAuthor());
          pn.setBasionymAuthorship(nu.getName().getBasionymAuthorship());
          pn.rebuildAuthorship();
          existing.usage.setAuthorship(pn.getAuthorship());
          LOG.debug("Updated {} with authorship {}", pn.getScientificName(), pn.getAuthorship());
        }
        if (!nu.getName().getRank().isUncomparable() && existing.usage.getRank().isUncomparable()
          && RankComparator.compareVagueRanks(existing.usage.getRank(), nu.getName().getRank()) != Equality.DIFFERENT
        ) {
          upd.add(InfoGroup.RANK);
          pn.setRank(nu.getName().getRank());
          existing.usage.setRank(pn.getRank());
          LOG.debug("Updated {} with rank {}", pn.getScientificName(), pn.getRank());
        }
        // also update the original match as we cache and reuse that
        if (nu.getName().getNamesIndexId() != existing.usage.getNamesIndexId()) {
          existing.usage.setNamesIndexId(nu.getName().getNamesIndexId());
          // update name match in db
          nmm.update(pn, nu.getName().getNamesIndexId(), nu.getName().getNamesIndexType());
          batchSession.commit(); // we need the matches to be up to date all the time! cache loaders...
        }
      }
      if (existing.usage.getPublishedInID() == null && nu.getName().getPublishedInId() != null) {
        pn = pn != null ? pn : loadFromDB(existing.usage.getId());
        upd.add(InfoGroup.PUBLISHED_IN);
        Reference ref = rm.get(DSID.of(nu.getDatasetKey(), nu.getName().getPublishedInId()));
        pn.setPublishedInId(lookupReference(ref));
        pn.setPublishedInPage(nu.getName().getPublishedInPage());
        pn.setPublishedInPageLink(nu.getName().getPublishedInPageLink());
        // also update the original match as we cache and reuse that
        existing.usage.setPublishedInID(pn.getPublishedInId());
        LOG.debug("Updated {} with publishedIn", pn);
      }

      // type material
      if (entities.contains(EntityType.TYPE_MATERIAL)) {
        // TODO: implement type material updates
      }

      // basionym / name relations
      if (entities.contains(EntityType.NAME_RELATION)) {
        // TODO: implement basionym/name rel updates
      }

      if (!upd.isEmpty()) {
        this.updated++;
        // update name
        nm.update(pn);
        // track source
        vsm.insertSources(existingUsageKey, nu, upd);
        batchSession.commit(); // we need the parsed names to be up to date all the time! cache loaders...
        matcher.invalidate(targetDatasetKey, existing.usage.getCanonicalId());
        return true;
      }
    } else {
      LOG.debug("Ignore update of {} {} {} from source {}:{} with different status {}", existing.usage.getStatus(), existing.usage.getRank(), existing.usage.getLabel(), sector.getSubjectDatasetKey(), nu.getId(), nu.getStatus());
    }
    return false;
  }

  /**
   * @param vn1 required to have a name & language!
   */
  private static boolean sameName(VernacularName vn1, VernacularName vn2) {
    return removeWhitespace(vn1.getName()).equalsIgnoreCase(removeWhitespace(vn2.getName())) ||
      ( vn1.getLatin() != null && removeWhitespace(vn1.getLatin()).equalsIgnoreCase(removeWhitespace(vn2.getLatin())) );
  }

  /**
   * Copies all name and taxon relations based on ids collected during the accept calls by the tree traversal.
   */
  @Override
  public void copyRelations() {
    // TODO: copy name & taxon relations
    // implicit relations last, so we can check if we have duplicates
    super.copyRelations();
  }

  @Override
  public void close() {
    matcher.removeLoader(targetDatasetKey);
    session.commit();
    session.close();
    batchSession.commit();
    batchSession.close();
    LOG.info("Sector {}: Total processed={}, thrown={}, ignored={}, created={}, updated={}", sector, counter, thrown, ignored, created, updated);
  }

}
