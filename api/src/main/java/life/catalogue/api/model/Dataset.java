package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Metadata about a dataset or a subset of it if parentKey is given.
 */
public class Dataset extends ArchivedDataset {

  private boolean locked = false;
  private boolean privat = false;
  private UUID gbifKey;
  private UUID gbifPublisherKey;
  private LocalDateTime imported; // from import table
  private LocalDateTime deleted;

  // human metadata
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Integer size;
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Set<Integer> contributesTo;

  public Dataset() {
  }

  public Dataset(ArchivedDataset other) {
    super(other);
  }

  public Dataset(Dataset other) {
    super(other);
    this.locked = other.locked;
    this.privat = other.privat;
    this.gbifKey = other.gbifKey;
    this.gbifPublisherKey = other.gbifPublisherKey;
    this.imported = other.imported;
    this.deleted = other.deleted;
    this.size = other.size;
    this.contributesTo = other.contributesTo;
  }

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
  
  public boolean isLocked() {
    return locked;
  }

  @JsonProperty("private")
  public boolean isPrivat() {
    return privat;
  }

  public void setPrivat(boolean privat) {
    this.privat = privat;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  public Integer getSize() {
    return size;
  }
  
  /**
   * If the dataset participates in any catalogue assemblies
   * this is indicated here by listing the catalogues datasetKey.
   * <p>
   * Dataset used to build the provisional catalogue will be trusted and insert their names into the names index.
   */
  public Set<Integer> getContributesTo() {
    return contributesTo;
  }
  
  public void setContributesTo(Set<Integer> contributesTo) {
    this.contributesTo = contributesTo;
  }
  
  public void addContributesTo(Integer catalogueKey) {
    if (contributesTo == null) {
      contributesTo = new HashSet<>();
    }
    contributesTo.add(catalogueKey);
  }

  /**
   * Time the data of the dataset was last changed in the Clearinghouse,
   * i.e. time of the last import that changed at least one record.
   */
  public LocalDateTime getImported() {
    return imported;
  }

  public void setImported(LocalDateTime imported) {
    this.imported = imported;
  }


  public LocalDateTime getDeleted() {
    return deleted;
  }

  @JsonIgnore
  public boolean hasDeletedDate() {
    return deleted != null;
  }
  
  public void setDeleted(LocalDateTime deleted) {
    this.deleted = deleted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Dataset)) return false;
    if (!super.equals(o)) return false;
    Dataset dataset = (Dataset) o;
    return locked == dataset.locked &&
      privat == dataset.privat &&
      Objects.equals(gbifKey, dataset.gbifKey) &&
      Objects.equals(gbifPublisherKey, dataset.gbifPublisherKey) &&
      Objects.equals(imported, dataset.imported) &&
      Objects.equals(deleted, dataset.deleted) &&
      Objects.equals(size, dataset.size) &&
      Objects.equals(contributesTo, dataset.contributesTo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), locked, privat, gbifKey, gbifPublisherKey, imported, deleted, size, contributesTo);
  }

  @Override
  public String toString() {
    return "Dataset " + getKey() + ": " + getTitle();
  }
}
