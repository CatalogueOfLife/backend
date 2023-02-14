package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.UsageCache;
import life.catalogue.matching.NameIndex;

import org.gbif.nameparser.api.Rank;

import java.util.*;

import org.apache.ibatis.session.SqlSessionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expects depth first traversal!
 */
public class TreeMergeHandler extends TreeBaseHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeMergeHandler.class);
  private final ParentStack parents;
  private final UsageMatcherGlobal matcher;
  private final UsageCache uCache;
  private int counter = 0;  // all source usages
  private int updCounter = 0; // updates
  private final int subjectDatasetKey;

  TreeMergeHandler(int targetDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, UsageMatcherGlobal matcher, User user, Sector sector, SectorImport state, Taxon incertae) {
    super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state);
    this.matcher = matcher;
    uCache = matcher.getUCache();
    if (target == null && incertae != null) {
      parents = new ParentStack(matcher.toSimpleName(incertae));
    } else {
      parents = new ParentStack(matcher.toSimpleName(target));
    }
    subjectDatasetKey = sector.getSubjectDatasetKey();
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
  public void accept(NameUsageBase nu) {
    // make rank non null
    if (nu.getName().getRank() == null) nu.getName().setRank(Rank.UNRANKED);
    // sector defaults before we apply a specific decision
    if (sector.getCode() != null) {
      nu.getName().setCode(sector.getCode());
    }
    // track parent classification and match to existing usages. Create new ones if they dont yet exist
    parents.put(matcher.toSimpleName(nu));
    LOG.debug("process {} {} {} -> {}", nu.getStatus(), nu.getName().getRank(), nu.getLabel(), parents.classification());
    counter++;
    // decisions
    if (decisions.containsKey(nu.getId())) {
      applyDecision(nu, decisions.get(nu.getId()));
    }

    // find out matching - even if we don't include the name in the merge we want the parents matched
    var match = matcher.matchWithParents(targetDatasetKey, nu, parents.classification());
    LOG.debug("{} matches {}", nu.getLabel(), match);
    // avoid the case when an accepted name without author is being matched against synonym names with authors from the same source
    if (match.isMatch()
        && nu.getStatus().isTaxon() && !nu.getName().hasAuthorship()
        && match.usage.getStatus().isSynonym() && match.usage.hasAuthorship()
        && Objects.equals(subjectDatasetKey, match.sourceDatasetKey)
    ) {
      LOG.debug("Ignore match to synonym {}. A canonical homonym from the same source for {}", match.usage.getLabel(), nu.getLabel());
      match = UsageMatch.empty(targetDatasetKey);
    }
    parents.setMatch(match.usage);

    if (ignoreUsage(nu, decisions.get(nu.getId()), match)) {
      // skip this taxon, but include children
      LOG.debug("Ignore {} {} [{}] type={}; status={}", nu.getName().getRank(), nu.getName().getLabel(), nu.getId(), nu.getName().getType(), nu.getName().getNomStatus());
      return;
    }

    // parent can be null if no matches exist
    Usage parent;
    // make sure synonyms have a matched direct parent (second last, cause the last is the synonym itself)
    if (nu.isSynonym()) {
      parent = usage(parents.secondLast().match);
    } else {
      parent = usage(parents.lowestParentMatch());
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
    } else {
      var sn = create(nu, parent);
      parents.setMatch(sn);
      matcher.add(nu);
    }

    // commit in batches
    if ((sCounter + tCounter + updCounter) % 1000 == 0) {
      session.commit();
      batchSession.commit();
    }
  }

  /**
   * Use the same usage matching to find existing taxa
   */
  @Override
  protected Usage findExisting(Name n) {
    // we need to commit the batch session to see the recent inserts
    batchSession.commit();
    Taxon t = new Taxon(n);
    var m = matcher.matchWithParents(targetDatasetKey, t, parents.classification());
    return usage(m.usage);
  }

  @Override
  protected void cacheImplicit(Taxon t, Usage parent) {
    matcher.add(t);
  }

  private Name loadFromDB(String usageID) {
    return nm.getByUsage(targetDatasetKey, usageID);
  }

  private boolean proposedParentDoesNotConflict(SimpleName existingParent, SimpleName proposedParent) {
    if (existingParent.getRank().higherThan(proposedParent.getRank())
           && !existingParent.getId().equals(proposedParent.getId())) {
      // now check the newly proposed classification does also contain the current parent to avoid changes - we only want to patch missing ranks
      var proposedClassification = uCache.getClassification(proposedParent.toDSID(targetDatasetKey), num::getSimplePub);
      for (var propHigherTaxon : proposedClassification) {
        if (propHigherTaxon.getId().equals(existingParent.getId())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean update(NameUsageBase nu, UsageMatch existing) {
    if (nu.getStatus() == existing.usage.getStatus()) {
      Set<InfoGroup> updated = EnumSet.noneOf(InfoGroup.class);
      // set targetKey to the existing usage
      targetKey.id(existing.usage.getId());
      // patch classification if direct parent adds to it
      var matchedParents = parents.matchedParentsOnly(existing.usage.getId());
      if (!matchedParents.isEmpty()) {
        var parent = matchedParents.getLast().match;
        var existingParent = existing.usage.getClassification() == null || existing.usage.getClassification().isEmpty() ? null : existing.usage.getClassification().get(0);
        batchSession.commit(); // we need to flush the write session to avoid broken foreign key constraints
        if (parent != null && (existingParent == null || proposedParentDoesNotConflict(existingParent, parent))) {
          LOG.debug("Updated {} with closer parent {} {} than {} from {}", existing.usage, parent.getRank(), parent.getId(), existingParent, nu);
          num.updateParentId(targetKey, parent.getId(), user.getKey());
          updated.add(InfoGroup.PARENT);
        }
      }

      // should we try to update the name? Need to load from db, so check upfront as much as possible to avoid db calls
      Name pn = null;
      if (!existing.usage.hasAuthorship() && nu.getName().hasAuthorship()) {
        pn = loadFromDB(existing.usage.getId());
        updated.add(InfoGroup.AUTHORSHIP);
        pn.setCombinationAuthorship(nu.getName().getCombinationAuthorship());
        pn.setSanctioningAuthor(nu.getName().getSanctioningAuthor());
        pn.setBasionymAuthorship(nu.getName().getBasionymAuthorship());
        pn.rebuildAuthorship();
        // also update the original match as we cache and reuse that
        existing.usage.setAuthorship(pn.getAuthorship());
        LOG.debug("Updated {} with authorship {}", pn.getScientificName(), pn.getAuthorship());
      }
      if (existing.usage.getPublishedInID() == null && nu.getName().getPublishedInId() != null) {
        pn = pn != null ? pn : loadFromDB(existing.usage.getId());
        updated.add(InfoGroup.PUBLISHED_IN);
        Reference ref = rm.get(DSID.of(nu.getDatasetKey(), nu.getName().getPublishedInId()));
        pn.setPublishedInId(lookupReference(ref));
        pn.setPublishedInPage(nu.getName().getPublishedInPage());
        pn.setPublishedInPageLink(nu.getName().getPublishedInPageLink());
        // also update the original match as we cache and reuse that
        existing.usage.setPublishedInID(pn.getPublishedInId());
        LOG.debug("Updated {} with publishedIn", pn);
      }
      // TODO: implement updates basionym, vernaculars, etc
      if (!updated.isEmpty()) {
        updCounter++;
        // update name
        nm.update(pn);
        // track source
        vm.insertSources(targetKey, nu, updated);
        return true;
      }
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
    session.commit();
    session.close();
    batchSession.commit();
    batchSession.close();
  }

}
