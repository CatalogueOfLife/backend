package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.matching.NameIndex;

import org.gbif.nameparser.api.*;

import java.util.*;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.api.util.ObjectUtils.coalesce;

/**
 * Expects depth first traversal!
 */
public class TreeMergeHandler extends TreeBaseHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeMergeHandler.class);
  private final ParentStack parents;
  private final UsageMatcher matcher;
  private int counter = 0;  // all source usages
  private int updCounter = 0; // updates
  private final DSID<String> targetKey = DSID.root(targetDatasetKey); // key to some target usage that can be reused

  TreeMergeHandler(Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, UsageMatcher matcher, User user, Sector sector, SectorImport state) {
    super(decisions, factory, nameIndex, user, sector, state);
    this.matcher = matcher;
    parents = new ParentStack(target);
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
    parents.put(nu);
    LOG.debug("process {} {} {} -> {}", nu.getStatus(), nu.getName().getRank(), nu.getLabel(), parents.classification());
    counter++;
    // decisions
    if (decisions.containsKey(nu.getId())) {
      applyDecision(nu, decisions.get(nu.getId()));
    }

    // find out matching - even if we dont include the name in the merge we want the parents matched
    var match = matcher.match(nu, parents.classification());
    LOG.debug("{} matches {}", nu.getLabel(), match);
    parents.setMatch(match);

    if (ignoreUsage(nu, decisions.get(nu.getId()))) {
      // skip this taxon, but include children
      LOG.debug("Ignore {} {} [{}] type={}; status={}", nu.getName().getRank(), nu.getName().getLabel(), nu.getId(), nu.getName().getType(), nu.getName().getNomStatus());
      return;
    }

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
    if (nu.getStatus() == TaxonomicStatus.ACCEPTED && (source.getType() == DatasetType.NOMENCLATURAL || parent.status.isSynonym())) {
      nu.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    }
    if (parent.status.isSynonym()) {
      // use accepted instead
      var sn = num.getSimpleParent(targetKey.id(parent.id));
      parent = usage(sn);
    }

    // finally create or update records
    if (match == null) {
      if (parent != null) {
        create(nu, parent);
        parents.setMatch(nu); // this is now the modified, created usage
        matcher.add(nu);
      }
    } else {
      update(nu, match);
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
    var m = matcher.match(t, parents.classification());
    return usage(m);
  }

  @Override
  protected void cacheImplicit(Taxon t, Usage parent) {
    matcher.add(t);
  }

  private void update(NameUsageBase nu, NameUsageBase existing) {
    if (nu.getStatus() == existing.getStatus()) {
      //TODO: implement updates for authorship, published in, vernaculars, basionym, etc
      // patch classification if direct parent adds to it
      LOG.debug("Update {} with {}", existing, nu);
      updCounter++;
    }
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
