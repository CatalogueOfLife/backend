package org.col.admin.importer.neo.traverse;

import org.neo4j.graphdb.Node;

/**
 * An event handler interface that accepts a start and end event for a normalizer node.
 * Used in taxonomic traversals to implement workers for a classification walk.
 */
public interface StartEndHandler {
  
  void start(Node n);
  
  void end(Node n);
}
