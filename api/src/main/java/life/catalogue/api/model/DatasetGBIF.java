package life.catalogue.api.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class DatasetGBIF extends DatasetSimple {
  private UUID gbifKey;
  // the GBIF registry modified timestamp we last synced, used to skip unchanged datasets
  private LocalDateTime gbifModified;

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
  }

  public LocalDateTime getGbifModified() {
    return gbifModified;
  }

  public void setGbifModified(LocalDateTime gbifModified) {
    this.gbifModified = gbifModified;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetGBIF)) return false;
    if (!super.equals(o)) return false;

    DatasetGBIF that = (DatasetGBIF) o;
    return Objects.equals(gbifKey, that.gbifKey)
           && Objects.equals(gbifModified, that.gbifModified);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), gbifKey, gbifModified);
  }
}
