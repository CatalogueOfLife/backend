package life.catalogue.api.model;

import life.catalogue.api.vocab.MatchType;

import java.util.Objects;

public class SimpleNameWithNidx extends SimpleName {
  private MatchType nameIndexMatchType;
  private Integer nameIndexId;

  public MatchType getNameIndexMatchType() {
    return nameIndexMatchType;
  }

  public void setNameIndexMatchType(MatchType nameIndexMatchType) {
    this.nameIndexMatchType = nameIndexMatchType;
  }

  public Integer getNameIndexId() {
    return nameIndexId;
  }

  public void setNameIndexId(Integer nameIndexId) {
    this.nameIndexId = nameIndexId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameWithNidx)) return false;
    if (!super.equals(o)) return false;
    SimpleNameWithNidx that = (SimpleNameWithNidx) o;
    return nameIndexMatchType == that.nameIndexMatchType &&
      Objects.equals(nameIndexId, that.nameIndexId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), nameIndexMatchType, nameIndexId);
  }
}
