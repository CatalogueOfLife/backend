package life.catalogue.importer.neo.traverse;

import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.api.model.Classification;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Includes only nodes that have ranks from Classification.RANKS.
 */
public class ClassificationRankEvaluator implements Evaluator {
  
  @Override
  public Evaluation evaluate(Path path) {
    Node nameNode = NeoProperties.getNameNode(path.endNode());
    Rank r = NeoProperties.getRank(nameNode, Rank.UNRANKED);
    return Classification.RANKS.contains(r) ? Evaluation.INCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_CONTINUE;
  }
}
