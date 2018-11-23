package org.col.admin.importer.neo.traverse;

import javax.annotation.Nullable;

import org.col.admin.importer.neo.model.NeoProperties;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Includes only paths with taxon end nodes that have a related name node with a rank equal or above the threshold given.
 */
public class UsageRankEvaluator implements Evaluator {

  private final @Nullable Rank threshold;

  public UsageRankEvaluator(Rank threshold) {
    this.threshold = threshold;
  }
  
  @Override
  public Evaluation evaluate(Path path) {
    return evaluateNode(path.endNode()) ? Evaluation.INCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_CONTINUE;
  }
  
  /**
   * @return true if the satisfies the rank evaluator and should be included.
   */
  public boolean evaluateNode(Node n) {
    if (threshold == null) return true;
    Rank rank = NeoProperties.getRank(NeoProperties.getNameNode(n), Rank.UNRANKED);
    return !threshold.higherThan(rank);
  }
}
