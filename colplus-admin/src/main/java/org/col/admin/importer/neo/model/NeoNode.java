package org.col.admin.importer.neo.model;

import java.util.Map;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

public interface NeoNode {
  
  Node getNode();

  void setNode(Node n);
  
  Label[] getLabels();
  
  Map<String, Object> properties();
  
  /**
   * Compares a NeoNode to another NeoNode just by its nodeId
   */
  default boolean equalNode(NeoNode other) {
    return getNode() == other.getNode() ||
        (getNode() != null && other.getNode() != null && getNode().getId() == other.getNode().getId());
  }
  
}
