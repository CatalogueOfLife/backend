package org.col.admin.importer.neo.model;

import java.util.Map;
import java.util.Objects;

import org.col.admin.importer.neo.NeoDbUtils;
import org.col.api.model.Name;
import org.col.api.model.VerbatimID;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

/**
 * Simple wrapper to hold a neo4j node together with a name
 */
public class NeoName implements NeoNode, VerbatimID {
  private static final Label[] LABELS = new Label[]{Labels.NAME};
  
  public Node node;
  public Name name;
  public boolean homotypic = false;
  
  public NeoName() {
  }
  
  public NeoName(Name name) {
    this.name = name;
  }

  public NeoName(Node node, Name name) {
    this.node = node;
    this.name = name;
  }
  
  @Override
  public Node getNode() {
    return node;
  }
  
  @Override
  public void setNode(Node node) {
    this.node = node;
  }
  
  @Override
  public Integer getVerbatimKey() {
    return name.getVerbatimKey();
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    name.setVerbatimKey(verbatimKey);
  }
  
  @Override
  public String getId() {
    return name.getId();
  }
  
  @Override
  public void setId(String id) {
    name.setId(id);
  }
  
  @Override
  public Label[] getLabels() {
    return LABELS;
  }
  
  @Override
  public Map<String, Object> properties() {
    return NeoDbUtils.neo4jProps(name);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeoName name1 = (NeoName) o;
    return homotypic == name1.homotypic &&
        Objects.equals(node, name1.node) &&
        Objects.equals(name, name1.name);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(node, name, homotypic);
  }
}