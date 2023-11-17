package life.catalogue.api.model;

import java.util.Objects;

/**
 * A Name relation that can includes usage keys for the respective nameId and relatedNameId values.
 * Not that there might be several usages for a given name. In that case the relation should be duplicated!
 */
public class NameUsageRelation extends NameRelation {
  private String usageId;
  private String relatedUsageId;

  public String getUsageId() {
    return usageId;
  }

  public void setUsageId(String usageId) {
    this.usageId = usageId;
  }

  public String getRelatedUsageId() {
    return relatedUsageId;
  }

  public void setRelatedUsageId(String relatedUsageId) {
    this.relatedUsageId = relatedUsageId;
  }

  @Override
  public boolean equals(Object o) {

    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameUsageRelation that = (NameUsageRelation) o;
    return Objects.equals(usageId, that.usageId) && Objects.equals(relatedUsageId, that.relatedUsageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), usageId, relatedUsageId);
  }
}
