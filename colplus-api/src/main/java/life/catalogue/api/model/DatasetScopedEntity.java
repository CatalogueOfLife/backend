package life.catalogue.api.model;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
  
  @Override
  @JsonIgnore
  public DSID<T> getKey() {
    return this;
  }
  
  @Override
  @JsonIgnore
  public void setKey(DSID<T> key) {
    if (key == null) {
      setDatasetKey(null);
      setId(null);
    } else {
      setDatasetKey(key.getDatasetKey());
      setId(key.getId());
    }
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
