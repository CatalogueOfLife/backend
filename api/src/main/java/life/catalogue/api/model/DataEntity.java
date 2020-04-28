package life.catalogue.api.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity that can be created and modified by a user.
 */
public abstract class DataEntity<K> implements Entity<K>, UserManaged {

  private LocalDateTime created;
  private Integer createdBy;
  private LocalDateTime modified;
  private Integer modifiedBy;
  
  public abstract K getKey();
  
  public abstract void setKey(K key);
  
  @Override
  public LocalDateTime getCreated() {
    return created;
  }

  @Override
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }

  @Override
  public Integer getCreatedBy() {
    return createdBy;
  }

  @Override
  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }

  @Override
  public LocalDateTime getModified() {
    return modified;
  }

  @Override
  public void setModified(LocalDateTime modified) {
    this.modified = modified;
  }

  @Override
  public Integer getModifiedBy() {
    return modifiedBy;
  }

  @Override
  public void setModifiedBy(Integer modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  /**
   * We ignore the timestamps - they often lead to cumbersome comparisons in tests
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DataEntity)) return false;
    DataEntity<?> that = (DataEntity<?>) o;
    return Objects.equals(createdBy, that.createdBy) &&
           Objects.equals(modifiedBy, that.modifiedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(createdBy, modifiedBy);
  }
}
