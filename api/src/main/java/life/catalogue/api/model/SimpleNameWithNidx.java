package life.catalogue.api.model;

import life.catalogue.api.vocab.MatchType;

import java.util.Objects;

public class SimpleNameWithNidx extends SimpleName {
  private Integer canonicalId;
  private Integer namesIndexId;
  private MatchType namesIndexMatchType;

  public MatchType getNamesIndexMatchType() {
    return namesIndexMatchType;
  }

  public void setNamesIndexMatchType(MatchType namesIndexMatchType) {
    this.namesIndexMatchType = namesIndexMatchType;
  }

  public Integer getNamesIndexId() {
    return namesIndexId;
  }

  public void setNamesIndexId(Integer namesIndexId) {
    this.namesIndexId = namesIndexId;
  }

  public Integer getCanonicalId() {
    return canonicalId;
  }

  public void setCanonicalId(Integer canonicalId) {
    this.canonicalId = canonicalId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameWithNidx)) return false;
    if (!super.equals(o)) return false;
    SimpleNameWithNidx that = (SimpleNameWithNidx) o;
    return Objects.equals(canonicalId, that.canonicalId) &&
      namesIndexMatchType == that.namesIndexMatchType &&
      Objects.equals(namesIndexId, that.namesIndexId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), canonicalId, namesIndexMatchType, namesIndexId);
  }

  @Override
  public String toString() {
    return "NIDX " + canonicalId + "-" +namesIndexId+ " ("+ namesIndexMatchType + ")";
  }
}
