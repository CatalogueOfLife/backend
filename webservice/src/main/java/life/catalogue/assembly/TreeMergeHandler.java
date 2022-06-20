package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.dao.CatCopy;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.NameIndex;
import life.catalogue.parser.NameParser;

import org.apache.commons.math3.random.StableRandomGenerator;

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

    // finally create or update records
    if (match == null) {
      var parent = usage(parents.lowestParentMatch());
      create(nu, parent);
    } else {
      update(nu, match);
    }

    // commit in batches
    if ((sCounter + tCounter + updCounter) % 1000 == 0) {
      session.commit();
      batchSession.commit();
    }
  }

  private void update(NameUsageBase nu, NameUsageBase existing) {
    //TODO: implement updates for authorship, published in, vernaculars, basionym, etc
    LOG.debug("Update {} with {}", existing, nu);
    updCounter++;
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
