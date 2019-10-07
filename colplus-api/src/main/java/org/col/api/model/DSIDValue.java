package org.col.api.model;

import java.util.Objects;

/**
 * Globally unique identifier across all datasets by combining the datasetKey and ID.
 * Useful for name, taxon and reference objects.
 */
public class DSIDValue<T> implements DSID<T> {
  
  private Integer datasetKey;
  private T id;
  
  public DSIDValue() {
  }
  
  public DSIDValue(int datasetKey, T id) {
    this.datasetKey = datasetKey;
    this.id = id;
  }
  
  public DSIDValue(DSID<T> id) {
    this.datasetKey = id.getDatasetKey();
    this.id = id.getId();
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public T getId() {
    return id;
  }

  public void setId(T id) {
    this.id = id;
  }
  
  /**
   * Builder style for fluent tests but reusing the same instance
   */
  public DSIDValue<T> id(T id) {
    this.id = id;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DSIDValue datasetID = (DSIDValue) o;
    return Objects.equals(datasetKey, datasetID.datasetKey) &&
        Objects.equals(id, datasetID.id);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id);
  }
  
  @Override
  public String toString() {
    return datasetKey + ":" + id;
  }
}
