package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

public class SimpleNameClassified extends SimpleNameWithPub {
  // classificaiton starting with direct parent
  private List<SimpleNameWithPub> classification;

  public SimpleNameClassified() {
  }

  public SimpleNameClassified(SimpleNameWithPub other) {
    super(other);
  }

  public List<SimpleNameWithPub> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleNameWithPub> classification) {
    this.classification = classification;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameClassified)) return false;
    if (!super.equals(o)) return false;
    SimpleNameClassified that = (SimpleNameClassified) o;
    return Objects.equals(classification, that.classification);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), classification);
  }
}
