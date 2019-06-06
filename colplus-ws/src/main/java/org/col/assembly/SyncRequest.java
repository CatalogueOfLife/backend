package org.col.assembly;

import java.util.Objects;

public class SyncRequest {
  private Integer sectorKey;
  private Integer datasetKey;
  
  public SyncRequest() {
  }
  
  public SyncRequest(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SyncRequest that = (SyncRequest) o;
    return Objects.equals(sectorKey, that.sectorKey) &&
        Objects.equals(datasetKey, that.datasetKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(sectorKey, datasetKey);
  }
}
