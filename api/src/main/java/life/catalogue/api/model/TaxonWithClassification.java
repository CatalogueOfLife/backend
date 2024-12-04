package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

public class TaxonWithClassification extends Taxon {
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
    if (!(o instanceof TaxonWithClassification)) return false;
    if (!super.equals(o)) return false;
    TaxonWithClassification that = (TaxonWithClassification) o;
    return Objects.equals(classification, that.classification);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), classification);
  }
}
