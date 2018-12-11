package org.col.api.model;

import java.util.Objects;

/**
 * Globally unique identifier across all datasets by combining the datasetKey and ID.
 * Useful for name, taxon and reference objects.
 */
public class DatasetID {
  private final Integer datasetKey;
  private final String id;
  
  public DatasetID(Integer datasetKey, String id) {
    this.datasetKey = datasetKey;
    this.id = id;
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public String getId() {
    return id;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DatasetID datasetID = (DatasetID) o;
    return Objects.equals(datasetKey, datasetID.datasetKey) &&
        Objects.equals(id, datasetID.id);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id);
  }
}
