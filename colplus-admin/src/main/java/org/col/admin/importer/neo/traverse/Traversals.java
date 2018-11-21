package org.col.admin.importer.neo.traverse;

import java.util.Set;

import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.RelType;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.kernel.impl.traversal.MonoDirectionalTraversalDescription;

/**
 * Various reusable traversal descriptions for taxonomic normalizer dbs.
 */
public class Traversals {
  public static final TraversalDescription PARENT = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.INCOMING)
      .depthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition());

  public static final TraversalDescription PARENTS = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.INCOMING)
      .depthFirst()
      .evaluator(Evaluators.excludeStartPosition());

  /**
   * Just the parents with ranks used in Classification.RANKS
   */
  public static final TraversalDescription CLASSIFICATION = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.INCOMING)
      .depthFirst()
      .evaluator(new ClassificationRankEvaluator())
      .evaluator(Evaluators.excludeStartPosition());

  public static final TraversalDescription CHILDREN = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.OUTGOING)
      .breadthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition())
      .uniqueness(Uniqueness.NODE_PATH);

  public static final TraversalDescription SYNONYMS = new MonoDirectionalTraversalDescription()
      .relationships(RelType.SYNONYM_OF, Direction.INCOMING)
      .breadthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition())
      .uniqueness(Uniqueness.NODE_PATH);

  public static final TraversalDescription ACCEPTED = new MonoDirectionalTraversalDescription()
      .relationships(RelType.SYNONYM_OF, Direction.OUTGOING)
      .breadthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition())
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Finds all nodes connected via homotypic name relations regardless of the direction.
   */
  public static final TraversalDescription HOMOTYPIC_GROUP;
  static {
    TraversalDescription td = new MonoDirectionalTraversalDescription();
    for (RelType rt : RelType.values()) {
      if (rt.nomRelType != null && Boolean.TRUE.equals(rt.nomRelType.isHomotypic())) {
        td = td.relationships(rt);
      }
    }
    HOMOTYPIC_GROUP = td
        .breadthFirst()
        .uniqueness(Uniqueness.NODE_RECENT)
        .evaluator(Evaluators.excludeStartPosition());
  }


  /**
   * Traversal that iterates depth first over all accepted descendants including the starting node.
   * There is no particular order for the direct children.
   * Use the SORTED_TREE traversals if a taxonomic order is required!
   */
  public static final TraversalDescription ACCEPTED_TREE = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.OUTGOING)
      .depthFirst()
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Traversal that iterates depth first over all descendants including synonyms and the starting node.
   * The node of pro parte synonyms will be visited multiple times.
   * There is no particular order for the direct children.
   * See SORTED_TREE traversals if a taxonomic order is required!
   */
  public static final TraversalDescription TREE = ACCEPTED_TREE
      .relationships(RelType.SYNONYM_OF, Direction.INCOMING);

  /**
   * Traversal that iterates depth first over all descendants including synonyms.
   * The node of pro parte synonyms will be visited multiple times, once for each synonym/pro_parte relationship!
   * There is no particular order for the direct children.
   * See SORTED_TREE if a taxonomic order is required!
   */
  public static final TraversalDescription DESCENDANTS = TREE
      .evaluator(Evaluators.excludeStartPosition());

  /**
   * Traversal that iterates over all child taxa and their synonyms in a taxonomic order, i.e. by rank and secondary ordered by the name.
   * The traversal includes the initial starting node.
   * The node of pro parte synonyms will be visited multiple times, once for each synonym/pro_parte relationship!
   * <p>
   * This traversal differes from DESCENDANTS that it includes the starting node and yields the nodes in a taxonomic order.
   * The order is a bit expensive to calculate and requires more memory. So use DESCENDANTS whenever possible.
   */
  public static final TraversalDescription SORTED_TREE = new MonoDirectionalTraversalDescription()
      .depthFirst()
      .expand(TaxonomicOrderExpander.TREE_WITH_SYNONYMS_EXPANDER)
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Traversal that iterates over all accepted child taxa in taxonomic order, i.e. by rank and secondary ordered by the name.
   * The traversal includes the initial starting node!
   */
  public static final TraversalDescription SORTED_ACCEPTED_TREE = new MonoDirectionalTraversalDescription()
      .depthFirst()
      .expand(TaxonomicOrderExpander.TREE_EXPANDER)
      .evaluator(new AcceptedOnlyEvaluator())
      .uniqueness(Uniqueness.NODE_PATH);


  /**
   * Tries to find the set of accepted nodes.
   * Can be multiple due to pro parte synonyms.
   *
   * @param syn synonym node to look for accepted nodes
   */
  public static Set<Node> acceptedOf(Node syn) {
    try (ResourceIterator<Node> accepted = Traversals.ACCEPTED.traverse(syn).nodes().iterator()) {
      return Iterators.asSet(accepted);
    }
  }

  /**
   * Tries to find the next direct parent node.
   *
   * @param start node to start looking for parents, excluded from search
   * @return the parent node or null
   */
  public static Node parentOf(Node start) {
    Relationship rel = start.getSingleRelationship(RelType.PARENT_OF, Direction.INCOMING);
    return rel == null ? null : rel.getStartNode();
  }

  /**
   * Tries to find a parent node with the given rank
   *
   * @param start node to start looking for parents, excluded from search
   * @return the parent node with requested rank or null
   */
  public static Node parentWithRankOf(Node start, Rank rank) {
    try (ResourceIterator<Node> parents = Traversals.PARENTS.traverse(start).nodes().iterator()) {
      while (parents.hasNext()) {
        Node p = parents.next();
        int rankOrd = (int) NeoProperties.getNameNode(p).getProperty(NeoProperties.RANK, -1);
        if (rankOrd == rank.ordinal()) {
          return p;
        } else if (!rank.isUncomparable() && rankOrd > 0 && rankOrd < rank.ordinal()) {
          // we are already passed the requested rank
          break;
        }
      }
    }
    return null;
  }
}
