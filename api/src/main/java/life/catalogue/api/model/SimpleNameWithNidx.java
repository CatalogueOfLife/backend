package life.catalogue.api.model;

import life.catalogue.api.vocab.MatchType;

import java.util.Objects;

public class SimpleNameWithNidx extends SimpleName {
  private Integer canonicalId;
  private Integer namesIndexId;
  private MatchType namesIndexMatchType;

  public SimpleNameWithNidx() {
  }

  public SimpleNameWithNidx(SimpleName other) {
    super(other);
  }

  public SimpleNameWithNidx(SimpleName other, Integer canonicalId) {
    super(other);
    this.canonicalId = canonicalId;
  }
  public SimpleNameWithNidx(SimpleNameWithNidx other) {
    super(other);
    canonicalId = other.canonicalId;
    namesIndexId = other.namesIndexId;
    namesIndexMatchType = other.namesIndexMatchType;
  }

  /**
   * @param canonicalId the canonicalId as its not included in a Name instance
   */
  public SimpleNameWithNidx(Name n, Integer canonicalId) {
    super(n);
    this.canonicalId = canonicalId;
    namesIndexId = n.getNamesIndexId();
    namesIndexMatchType = n.getNamesIndexType();
  }

  /**
   * @param canonicalId the canonicalId as its not included in a Name instance
   */
  public SimpleNameWithNidx(NameUsageBase u, Integer canonicalId) {
    super(u);
    this.canonicalId = canonicalId;
    namesIndexId = u.getName().getNamesIndexId();
    namesIndexMatchType = u.getName().getNamesIndexType();
  }

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

  public void applyMatch(NameMatch m) {
    if (m.hasMatch()) {
      setNamesIndexMatchType(m.getType());
      setNamesIndexId(m.getName().getKey());
      setCanonicalId(m.getName().getCanonicalId());

    } else {
      setNamesIndexMatchType(MatchType.NONE);
      setNamesIndexId(null);
      setCanonicalId(null);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameWithNidx)) return false;
    if (!super.equals(o)) return false;
    SimpleNameWithNidx that = (SimpleNameWithNidx) o;
    return Objects.equals(canonicalId, that.canonicalId) && Objects.equals(namesIndexId, that.namesIndexId) && namesIndexMatchType == that.namesIndexMatchType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), canonicalId, namesIndexId, namesIndexMatchType);
  }

  @Override
  public void toStringAdditionalInfo(StringBuilder sb) {
    sb.append(" | nidx ");
    sb.append(canonicalId);
    sb.append('-');
    sb.append(namesIndexId);
  }

}
