package org.col.commands.importer.neo.traverse;

import org.col.api.vocab.Rank;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import javax.annotation.Nullable;

/**
 * Utils to persistent Iterables for nodes to traverse a taxonomic tree in taxonomic order but with unsorted leaf nodes.
 */
public class TreeIterables {

  public static Iterable<Node> allNodes(GraphDatabaseService db, @Nullable Node root, @Nullable Rank lowestRank) {
    return MultiRootNodeIterator.create(TreeIterablesSorted.findRoot(db, root), TreeIterablesSorted.filterRank(Traversals.TREE, lowestRank));
  }

}
