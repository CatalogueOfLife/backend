package life.catalogue.api.model;

import java.util.Objects;

public class SimpleNameVerbatim extends SimpleName {
  private Integer verbatimSourceKey;

  public Integer getVerbatimSourceKey() {
    return verbatimSourceKey;
  }

  public void setVerbatimSourceKey(Integer verbatimSourceKey) {
    this.verbatimSourceKey = verbatimSourceKey;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SimpleNameVerbatim)) return false;
    if (!super.equals(o)) return false;

    SimpleNameVerbatim that = (SimpleNameVerbatim) o;
    return Objects.equals(verbatimSourceKey, that.verbatimSourceKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimSourceKey);
  }
}
