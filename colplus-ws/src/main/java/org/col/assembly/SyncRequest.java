package org.col.assembly;

import java.util.Objects;

public class SyncRequest {
  private Boolean all;
  private Integer sectorKey;
  private Integer datasetKey;
  
  public static SyncRequest all() {
    SyncRequest req = new SyncRequest();
    req.setAll(true);
    return req;
  }
  
  public static SyncRequest sector(int sectorKey) {
    SyncRequest req = new SyncRequest();
    req.setSectorKey(sectorKey);
    return req;
  }
  
  public static SyncRequest dataset(int datasetKey) {
    SyncRequest req = new SyncRequest();
    req.setDatasetKey(datasetKey);
    return req;
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
  
  public Boolean getAll() {
    return all;
  }
  
  public void setAll(Boolean all) {
    this.all = all;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SyncRequest that = (SyncRequest) o;
    return Objects.equals(all, that.all) &&
        Objects.equals(sectorKey, that.sectorKey) &&
        Objects.equals(datasetKey, that.datasetKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(all, sectorKey, datasetKey);
  }
}
