package life.catalogue.importer.neo.model;

import java.util.Arrays;
import java.util.HashMap;

import com.google.common.base.Joiner;
import org.neo4j.graphdb.Label;

public class PropLabel extends HashMap<String,Object> {
  private static final Joiner.MapJoiner PROP_JOINER = Joiner.on(", ").withKeyValueSeparator('=').useForNull("NULL");
  private Label[] labels;
  
  public PropLabel(Label... labels) {
    this.labels = labels;
  }
  
  public Label[] getLabels() {
    return labels;
  }
  
  @Override
  public boolean isEmpty() {
    return super.isEmpty() && (labels == null || labels.length == 0);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PropLabel propLabel = (PropLabel) o;
    return Arrays.equals(labels, propLabel.labels);
  }
  
  @Override
  public int hashCode() {
    
    int result = super.hashCode();
    result = 31 * result + Arrays.hashCode(labels);
    return result;
  }
  
  @Override
  public String toString() {
    return Arrays.toString(labels) + ": " + PROP_JOINER.join(this);
  }
}
