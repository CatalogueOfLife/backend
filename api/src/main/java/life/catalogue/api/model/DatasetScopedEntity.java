package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.annotation.Nonnull;

public class DatasetScopedEntity<T> extends DataEntity<DSID<T>> implements DSID<T> {
  
  /**
   * Key to dataset instance. Defines context of the id.
   */
  @Nonnull
  private Integer datasetKey;

  /**
   * Primary key of the entity scoped within a dataset and can follow any kind of schema.
   */
  private T id;

  public DatasetScopedEntity() {
  }

  public DatasetScopedEntity(DSID<T> key) {
    this.datasetKey = key.getDatasetKey();
    this.id = key.getId();
  }

  public DatasetScopedEntity(DatasetScopedEntity<T> other) {
    super(other);
    this.datasetKey = other.datasetKey;
    this.id = other.id;
  }

  @Override
  @JsonIgnore
  public DSID<T> getKey() {
    return this;
  }

  @Override
  @JsonIgnore
  public void setKey(DSID<T> key) {
    DSID.super.setKey(key);
  }
  
  @Override
  public T getId() {
    return id;
  }
  
  @Override
  public void setId(T id) {
    this.id = id;
  }
  
  @Override
  @Nonnull
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  @Override
  public void setDatasetKey(@Nonnull Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
}
