package life.catalogue.api.model;

import java.util.Objects;

public class SimpleNameWithPub extends SimpleNameWithNidx {
  private String publishedInID;

  public SimpleNameWithPub() {
  }

  public SimpleNameWithPub(SimpleNameWithPub other) {
    super(other);
    this.publishedInID = other.publishedInID;
  }

  public SimpleNameWithPub(NameUsageBase u, Integer canonicalId, String publishedInID) {
    super(u, canonicalId);
    this.publishedInID = publishedInID;
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
    if (!(o instanceof SimpleNameWithPub)) return false;
    if (!super.equals(o)) return false;
    SimpleNameWithPub that = (SimpleNameWithPub) o;
    return Objects.equals(publishedInID, that.publishedInID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), publishedInID);
  }
}
