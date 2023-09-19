package life.catalogue.api.model;

import java.util.Objects;

public class SimpleNameCached extends SimpleNameWithNidx {
  private Integer sectorKey;
  private String publishedInID;

  public SimpleNameCached() {
  }

  public SimpleNameCached(SimpleName other) {
    super(other);
  }

  public SimpleNameCached(SimpleNameCached other) {
    super(other);
    this.sectorKey = other.sectorKey;
    this.publishedInID = other.publishedInID;
  }

  public SimpleNameCached(NameUsageBase u, Integer canonicalId) {
    super(u, canonicalId);
    this.publishedInID = u.getName().getPublishedInId();
    this.sectorKey = u.getSectorKey();
  }

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  public String getPublishedInID() {
    return publishedInID;
  }

  public void setPublishedInID(String publishedInID) {
    this.publishedInID = publishedInID;
  }

  @Override
  public void toStringAdditionalInfo(StringBuilder sb) {
    super.toStringAdditionalInfo(sb);
    sb.append(" | ref ");
    sb.append(publishedInID);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameCached)) return false;
    if (!super.equals(o)) return false;
    SimpleNameCached that = (SimpleNameCached) o;
    return Objects.equals(sectorKey, that.sectorKey) && Objects.equals(publishedInID, that.publishedInID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, publishedInID);
  }
}
