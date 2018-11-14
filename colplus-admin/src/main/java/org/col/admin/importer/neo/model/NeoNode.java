package org.col.admin.importer.neo.model;

import java.util.Map;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

public interface NeoNode {
  
  Node getNode();

  void setNode(Node n);
  
  Label[] getLabels();
  
  Map<String, Object> properties();
  
}
