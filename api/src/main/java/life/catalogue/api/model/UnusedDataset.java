package life.catalogue.api.model;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A private project or external dataset nobody has used for a while, with the last time it was.
 * Used is read from small tables only: the dataset was created, changed its metadata, was imported or, for a project,
 * synced a sector.
 * Edits of the data records themselves are not seen.
 */
public class UnusedDataset {
  private Integer key;
  private String alias;
  private String title;
  private DatasetType type;
  private DatasetOrigin origin;
  private Integer createdBy;
  private String createdByName;
  private LocalDateTime created;
  private LocalDateTime lastUsed;

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public DatasetType getType() {
    return type;
  }

  public void setType(DatasetType type) {
    this.type = type;
  }

  public DatasetOrigin getOrigin() {
    return origin;
  }

  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }

  /**
   * @return the username of the creator
   */
  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
  }

  public LocalDateTime getCreated() {
    return created;
  }

  public void setCreated(LocalDateTime created) {
    this.created = created;
  }

  /**
   * @return the last time the dataset was created, changed its metadata, was imported or, for a project, synced a sector
   */
  public LocalDateTime getLastUsed() {
    return lastUsed;
  }

  public void setLastUsed(LocalDateTime lastUsed) {
    this.lastUsed = lastUsed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UnusedDataset that)) return false;
    return Objects.equals(key, that.key) && Objects.equals(alias, that.alias) && Objects.equals(title, that.title)
      && type == that.type && origin == that.origin && Objects.equals(createdBy, that.createdBy)
      && Objects.equals(createdByName, that.createdByName) && Objects.equals(created, that.created)
      && Objects.equals(lastUsed, that.lastUsed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, alias, title, type, origin, createdBy, createdByName, created, lastUsed);
  }

  @Override
  public String toString() {
    return "UnusedDataset{" + key + " " + title + ", lastUsed=" + lastUsed + '}';
  }
}
