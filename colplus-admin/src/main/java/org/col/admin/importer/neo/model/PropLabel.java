package org.col.admin.importer.neo.model;

import java.util.HashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.neo4j.graphdb.Label;

public class PropLabel extends HashMap<String,Object> {
  private Label[] labels;
  
  public PropLabel() {
    labels = null;
  }

  public PropLabel(Label... labels) {
    this.labels = labels;
  }
  
  public Label[] getLabels() {
    return labels;
  }
  
  public void addLabels(Label[] labels) {
    if (labels != null) {
      this.labels = ArrayUtils.addAll(this.labels, labels);
    }
  }
}
