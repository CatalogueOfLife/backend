package life.catalogue.api.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A dataset ChecklistBank registered itself in the GBIF registry.
 * The GBIF key and publishing organisation also live on the dataset, which stays the source of truth for both.
 * The GBIF key is kept here as well, because a soft deletion of the dataset nulls it there, while the deletion still
 * has to be passed on to GBIF.
 */
public class GbifRegistration {
  private Integer datasetKey;
  private UUID gbifKey;
  // the user who registered the dataset and thereby accepted the GBIF data publisher agreement
  private Integer createdBy;
  private LocalDateTime created;

  public GbifRegistration() {
  }

  public GbifRegistration(Integer datasetKey, UUID gbifKey, Integer createdBy) {
    this.datasetKey = datasetKey;
    this.gbifKey = gbifKey;
    this.createdBy = createdBy;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
  }

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreated() {
    return created;
  }

  public void setCreated(LocalDateTime created) {
    this.created = created;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GbifRegistration that)) return false;
    return Objects.equals(datasetKey, that.datasetKey) && Objects.equals(gbifKey, that.gbifKey)
      && Objects.equals(createdBy, that.createdBy) && Objects.equals(created, that.created);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, gbifKey, createdBy, created);
  }

  @Override
  public String toString() {
    return "GbifRegistration{" + datasetKey + " -> " + gbifKey + '}';
  }
}
