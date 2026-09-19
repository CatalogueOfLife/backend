package life.catalogue.api.model;

import java.util.Objects;

public class SimpleNameWithNidx extends SimpleName {
  private Integer namesIndexId;

  public SimpleNameWithNidx() {
  }

  public SimpleNameWithNidx(SimpleName other) {
    super(other);
  }

  public SimpleNameWithNidx(SimpleNameWithNidx other) {
    super(other);
    namesIndexId = other.namesIndexId;
  }

  /**
   * @param namesIndexId the names index id, which replaces the one of the name
   */
  public SimpleNameWithNidx(Name n, Integer namesIndexId) {
    super(n);
    this.namesIndexId = namesIndexId;
  }

  /**
   * @param namesIndexId the names index id, which replaces the one of the usages name
   */
  public SimpleNameWithNidx(NameUsageBase u, Integer namesIndexId) {
    super(u);
    this.namesIndexId = namesIndexId;
  }

  public Integer getNamesIndexId() {
    return namesIndexId;
  }

  public void setNamesIndexId(Integer namesIndexId) {
    this.namesIndexId = namesIndexId;
  }

  public void applyMatch(NameMatch m) {
    // the nidx match carries no MatchType; the usage-match layer computes EXACT/VARIANT
    // from the live labels, so we only apply the id here (see UsageMatcher)
    setNamesIndexId(m.isMatched() ? m.getNidx() : null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameWithNidx)) return false;
    if (!super.equals(o)) return false;
    SimpleNameWithNidx that = (SimpleNameWithNidx) o;
    return Objects.equals(namesIndexId, that.namesIndexId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), namesIndexId);
  }

  @Override
  public void toStringAdditionalInfo(StringBuilder sb) {
    sb.append(" | nidx ");
    sb.append(namesIndexId);
  }

}
