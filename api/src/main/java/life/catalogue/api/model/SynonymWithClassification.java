package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

/**
 * A synonym with the classification of its accepted taxon, ordered root first and ending with the accepted taxon itself.
 */
public class SynonymWithClassification extends Synonym {
  private List<SimpleName> classification;

  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SynonymWithClassification)) return false;
    if (!super.equals(o)) return false;
    SynonymWithClassification that = (SynonymWithClassification) o;
    return Objects.equals(classification, that.classification);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), classification);
  }
}
