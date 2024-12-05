package life.catalogue.api.model;

import java.util.Objects;

public class SimpleNameCached extends SimpleNameWithNidx {
  private Integer sectorKey;
  // temporary marker not being copied or persisted
  // used to mark usages temporarily during merging / processing. Not related to rank markers!
  public boolean marked;

  public SimpleNameCached() {
  }

  public SimpleNameCached(SimpleName other) {
    super(other);
  }

  public SimpleNameCached(SimpleNameWithNidx other) {
    super(other);
  }
  public SimpleNameCached(SimpleNameCached other) {
    super(other);
    this.sectorKey = other.sectorKey;
    this.marked = other.marked;
  }

  public SimpleNameCached(NameUsageBase u, Integer canonicalId) {
    super(u, canonicalId);
    this.sectorKey = u.getSectorKey();
  }

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  @Override
  public void toStringAdditionalInfo(StringBuilder sb) {
    super.toStringAdditionalInfo(sb);
    sb.append(" | sec ");
    sb.append(sectorKey);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameCached)) return false;
    if (!super.equals(o)) return false;
    SimpleNameCached that = (SimpleNameCached) o;
    return Objects.equals(sectorKey, that.sectorKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey);
  }
}
