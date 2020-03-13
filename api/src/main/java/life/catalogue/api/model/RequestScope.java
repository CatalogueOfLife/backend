package life.catalogue.api.model;

import java.util.Objects;

public class RequestScope {
  private Boolean all;
  private Integer sectorKey;
  private Integer datasetKey;
  
  public static RequestScope all() {
    RequestScope req = new RequestScope();
    req.setAll(true);
    return req;
  }
  
  public static RequestScope sector(int sectorKey) {
    RequestScope req = new RequestScope();
    req.setSectorKey(sectorKey);
    return req;
  }
  
  public static RequestScope dataset(int datasetKey) {
    RequestScope req = new RequestScope();
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
    RequestScope that = (RequestScope) o;
    return Objects.equals(all, that.all) &&
        Objects.equals(sectorKey, that.sectorKey) &&
        Objects.equals(datasetKey, that.datasetKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(all, sectorKey, datasetKey);
  }
}
