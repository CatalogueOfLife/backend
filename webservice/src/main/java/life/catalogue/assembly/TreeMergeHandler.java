package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.matching.MatchedParentStack;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcherGlobal;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.Supplier;

import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

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
                   Supplier<String> nameIdGen, Supplier<String> usageIdGen, Supplier<String> typeMaterialIdGen) {
    // we use much smaller ids than UUID which are terribly long to iterate over the entire tree - which requires to build a path from all parent IDs
    // this causes postgres to use a lot of memory and creates very large temporary files
    super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state, nameIdGen, usageIdGen, typeMaterialIdGen);
    this.cfg = cfg;
    this.vKey = DSID.root(sourceDatasetKey);
    this.matcher = matcher;
    uCache = matcher.getUCache();
    if (target == null && cfg != null && cfg.incertae != null) {
      parents = new MatchedParentStack(matcher.toSimpleName(cfg.incertae));
    } else {
      parents = new MatchedParentStack(matcher.toSimpleName(target));
    }
    this.loader = new CacheLoader.Mybatis(batchSession, true);
    matcher.registerLoader(targetDatasetKey, loader); // we need to make sure we remove it at the end no matter what!
  }


  @Override
  public void reset() {
    // only needed for UNION sectors which do several iterations
    throw new UnsupportedOperationException();
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
      LOG.warn("Unable to process {} with parent {}. {}:{}", nu, nu.getParentId(), e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;

    } catch (Exception e) {
      LOG.warn("Fatal error. Unable to process {} with parent {}. {}:{}", nu, nu.getParentId(), e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;
      throw new RuntimeException(e);
    }
  }

  public void acceptThrowsNoCatch(NameUsageBase nu) throws Exception {
    // apply common changes to the usage
    processCommon(nu);

    // track parent classification and match to existing usages. Create new ones if they dont yet exist
    var nusn = matcher.toSimpleName(nu);
    parents.push(nusn);
    counter++;
    LOG.debug("process {} {} {} -> {}", nu.getStatus(), nu.getName().getRank(), nu.getLabel(), parents.classificationToString());

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
      // as last resort this yields the parent stacks root taxon, e.g. incertae sedis
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
    if (match.ignore || ignoreUsage(nu, decisions.get(nu.getId()))) {
      // skip this taxon, but include children
      ignored++;
      return;
    }

    // replace accepted taxa with doubtful ones for all nomenclators and for synonym parents
    // and allow to manually configure a doubtful status
    // http://dev.gbif.org/issues/browse/POR-2780
    if (nu.getStatus() == TaxonomicStatus.ACCEPTED && (source.getType() == DatasetType.NOMENCLATURAL || parent != null && parent.status.isSynonym())) {
      nu.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    }
    if (parent != null && parent.status.isSynonym()) {
      // use accepted instead
      var sn = num.getSimpleParent(targetKey.id(parent.id));
      parent = usage(sn);
    }

    // finally create or update records
    if (match.isMatch()) {
      update(nu, match);
    } else if (match.type != MatchType.AMBIGUOUS) {
      // only add a new name if we do not have already multiple names that we cannot clearly match
      // track if we are outside of the sector target
      Issue[] issues;
      if (sector.getTarget() != null && parent != null
        && !containsID(uCache.getClassification(targetKey.id(parent.id), loader), sector.getTarget().getId())) {
        issues = new Issue[]{Issue.SYNC_OUTSIDE_TARGET};
      } else {
        issues = new Issue[0];
      }
      var p = create(nu, parent, issues);
      parents.setMatch(p);
      matcher.add(nu);
      created++;
    }

    // commit in batches
    if ((sCounter + tCounter + updated) % 1000 == 0) {
      interruptIfCancelled();
      session.commit();
      batchSession.commit();
    }
  }

  private static boolean containsID(List<SimpleNameCached> usages,  String id){
    return usages != null && usages.stream().anyMatch(u -> u.getId().equals(id));
  }

  @Override
  protected boolean ignoreUsage(NameUsageBase u, @Nullable EditorialDecision decision) {
    var ignore =  super.ignoreUsage(u, decision);
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
      }

      // should we try to update the name? Need to load from db, so check upfront as much as possible to avoid db calls
      Name pn = null;
      if (nu.getName().hasAuthorship() && !existing.usage.hasAuthorship()) {
        pn = loadFromDB(existing.usage.getId());
        upd.add(InfoGroup.AUTHORSHIP);
        pn.setCombinationAuthorship(nu.getName().getCombinationAuthorship());
        pn.setSanctioningAuthor(nu.getName().getSanctioningAuthor());
        pn.setBasionymAuthorship(nu.getName().getBasionymAuthorship());
        pn.rebuildAuthorship();
        // also update the original match as we cache and reuse that
        existing.usage.setAuthorship(pn.getAuthorship());
        if (nu.getName().getNamesIndexId() != existing.usage.getNamesIndexId()) {
          existing.usage.setNamesIndexId(nu.getName().getNamesIndexId());
          // update name match in db
          nmm.update(pn, nu.getName().getNamesIndexId(), nu.getName().getNamesIndexType());
          batchSession.commit(); // we need the matches to be up to date all the time! cache loaders...
        }
        LOG.debug("Updated {} with authorship {}", pn.getScientificName(), pn.getAuthorship());
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
      // TODO: implement updates basionym, vernaculars, etc
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
   * Copies all name and taxon relations based on ids collected during the accept calls by the tree traversal.
   */
  @Override
  public void copyRelations() {
    // TODO: copy name & taxon relations
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
