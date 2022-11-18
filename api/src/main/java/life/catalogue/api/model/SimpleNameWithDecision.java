package life.catalogue.api.model;

import java.util.Objects;

public class SimpleNameWithDecision extends SimpleName {
  private EditorialDecision decision;

  public EditorialDecision getDecision() {
    return decision;
  }

  public void setDecision(EditorialDecision decision) {
    this.decision = decision;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameWithDecision)) return false;
    if (!super.equals(o)) return false;
    SimpleNameWithDecision that = (SimpleNameWithDecision) o;
    return Objects.equals(decision, that.decision);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), decision);
  }
}
