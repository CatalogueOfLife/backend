package org.col.admin.task.importer.neo.traverse;

import java.util.List;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to iterate over nodes in taxonomic order and execute any number of StartEndHandler while walking.
 */
public class TreeWalker {

  private static final Logger LOG = LoggerFactory.getLogger(TreeWalker.class);
  private static final int reportingSize = 10000;

  /**
   * Walks all nodes of the taxonomic tree in a depth first order in a single transaction including multiple times the same pro parte node
   */
  public static void walkTree(GraphDatabaseService db, StartEndHandler... handler) throws InterruptedException {
    walkTree(db, Traversals.TREE, handler);
  }

  /**
   * Walks all nodes of the taxonomic tree in a taxonomic order in a single transaction including multiple times the same pro parte node
   */
  public static void walkSortedTree(GraphDatabaseService db, StartEndHandler... handler) throws InterruptedException {
    walkTree(db, Traversals.SORTED_TREE, handler);
  }

  public static void walkTree(GraphDatabaseService db,
                                TraversalDescription td,
                                StartEndHandler... handler
  ) throws InterruptedException {
    try (Transaction tx = db.beginTx()) {
      walkPaths(MultiRootPathIterator.create(findRoot(db, null), td), handler);
    }
  }

  public static void walkTree(GraphDatabaseService db,
                                TraversalDescription td,
                                @Nullable Node root,
                                @Nullable Rank lowestRank,
                                StartEndHandler... handler
  ) throws InterruptedException {
    try (Transaction tx = db.beginTx()) {
      walkPaths(MultiRootPathIterator.create(findRoot(db, root), filterRank(td, lowestRank)), handler);
    }
  }

  private static void walkPaths(ResourceIterable<Path> paths, StartEndHandler... handler) throws InterruptedException {
    Path lastPath = null;
    long counter = 0;
    try (ResourceIterator<Path> iter = paths.iterator()) {
      while (iter.hasNext()) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException("TreeWalker thread was cancelled/interrupted");
        }
        Path p = iter.next();
        //logPath(p);
        if (counter % reportingSize == 0) {
          LOG.debug("Processed {}", counter);
        }
        if (lastPath != null) {
          PeekingIterator<Node> lIter = Iterators.peekingIterator(lastPath.nodes().iterator());
          PeekingIterator<Node> cIter = Iterators.peekingIterator(p.nodes().iterator());
          while (lIter.hasNext() && cIter.hasNext() && lIter.peek().equals(cIter.peek())) {
            lIter.next();
            cIter.next();
          }
          // only non shared nodes left.
          // first close allAccepted old nodes, then open new ones
          // reverse order for closing nodes...
          for (Node n : ImmutableList.copyOf(lIter).reverse()) {
            handleEnd(n, handler);
          }
          while (cIter.hasNext()) {
            handleStart(cIter.next(), handler);
          }

        } else {
          // only new nodes
          for (Node n : p.nodes()) {
            handleStart(n, handler);
          }
        }
        lastPath = p;
        counter++;
      }
      // close all remaining nodes
      if (lastPath != null) {
        for (Node n : ImmutableList.copyOf(lastPath.nodes()).reverse()) {
          handleEnd(n, handler);
        }
      }
    }
  }

  private static void handleStart(Node n, StartEndHandler... handler) {
    for (StartEndHandler h : handler) {
      h.start(n);
    }
  }

  private static void handleEnd(Node n, StartEndHandler... handler) {
    for (StartEndHandler h : handler) {
      h.end(n);
    }
  }

  private static List<Node> findRoot(GraphDatabaseService db, @Nullable Node root) {
    if (root != null) {
      return Lists.newArrayList(root);
    }
    return org.neo4j.helpers.collection.Iterators.asList(db.findNodes(Labels.ROOT));
  }

  private static TraversalDescription filterRank(TraversalDescription td, @Nullable Rank lowestRank) {
    if (lowestRank != null) {
      return td.evaluator(new RankEvaluator(lowestRank));
    }
    return td;
  }

  private static void logPath(Path p) {
    StringBuilder sb = new StringBuilder();
    for (Node n : p.nodes()) {
      if (sb.length() > 0) {
        sb.append(" -- ");
      }
      sb.append(NeoProperties.getScientificName(n));
    }
    LOG.info(sb.toString());
  }

}
