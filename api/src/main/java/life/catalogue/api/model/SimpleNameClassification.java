package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

/**
 * A flexible classification as a list of SimpleName objects.
 */
public class SimpleNameClassification {
  private String id;
  private List<SimpleName> classification;
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  /**
   * The entire classification for the usage starting with the highest root and
   * including the taxon or synonym itself as the last entry in the list
   */
  public List<SimpleName> getClassification() {
    return classification;
  }
  
  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SimpleNameClassification that = (SimpleNameClassification) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(classification, that.classification);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(id, classification);
  }
}
