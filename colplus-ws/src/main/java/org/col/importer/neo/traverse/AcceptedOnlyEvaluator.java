package org.col.importer.neo.traverse;


import org.col.importer.neo.model.Labels;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Includes only paths that have accepted taxon end nodes.
 */
public class AcceptedOnlyEvaluator implements Evaluator {
  
  @Override
  public Evaluation evaluate(Path path) {
    Node end = path.endNode();
    return end.hasLabel(Labels.SYNONYM) ? Evaluation.EXCLUDE_AND_CONTINUE : Evaluation.INCLUDE_AND_CONTINUE;
  }
}
