package life.catalogue.api.model;

import java.util.Objects;
import java.util.UUID;

public class DatasetGBIF extends DatasetSimple {
  private UUID gbifKey;
  private UUID gbifPublisherKey;

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
  }

  public UUID getGbifPublisherKey() {
    return gbifPublisherKey;
  }

  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    this.gbifPublisherKey = gbifPublisherKey;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    DatasetGBIF that = (DatasetGBIF) o;
    return Objects.equals(gbifKey, that.gbifKey) && Objects.equals(gbifPublisherKey, that.gbifPublisherKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), gbifKey, gbifPublisherKey);
  }
}
