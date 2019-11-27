package life.catalogue.api.model;

import java.util.Objects;

public class RematchRequest extends RequestScope {
  private Integer decisionKey;
  private Integer estimateKey;
  
  public static RematchRequest all() {
    RematchRequest req = new RematchRequest();
    req.setAll(true);
    return req;
  }
  
  public static RematchRequest sector(int sectorKey) {
    RematchRequest req = new RematchRequest();
    req.setSectorKey(sectorKey);
    return req;
  }
  
  public static RematchRequest decision(int decisionKey) {
    RematchRequest req = new RematchRequest();
    req.setDecisionKey(decisionKey);
    return req;
  }
  
  public static RematchRequest estimate(int estimateKey) {
    RematchRequest req = new RematchRequest();
    req.setEstimateKey(estimateKey);
    return req;
  }
  
  public static RematchRequest dataset(int datasetKey) {
    RematchRequest req = new RematchRequest();
    req.setDatasetKey(datasetKey);
    return req;
  }
  
  public Integer getDecisionKey() {
    return decisionKey;
  }
  
  public void setDecisionKey(Integer decisionKey) {
    this.decisionKey = decisionKey;
  }
  
  public Integer getEstimateKey() {
    return estimateKey;
  }
  
  public void setEstimateKey(Integer estimateKey) {
    this.estimateKey = estimateKey;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    RematchRequest that = (RematchRequest) o;
    return
        Objects.equals(decisionKey, that.decisionKey) &&
        Objects.equals(estimateKey, that.estimateKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), decisionKey, estimateKey);
  }
}
