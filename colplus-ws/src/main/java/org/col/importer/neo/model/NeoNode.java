package org.col.importer.neo.model;

import org.neo4j.graphdb.Node;

public interface NeoNode {
  
  Node getNode();

  void setNode(Node n);
  
  PropLabel propLabel();
  
  /**
   * Compares a NeoNode to another NeoNode just by its nodeId
   */
  default boolean equalNode(NeoNode other) {
    return getNode() == other.getNode() ||
        (getNode() != null && other.getNode() != null && getNode().getId() == other.getNode().getId());
  }
  
}
