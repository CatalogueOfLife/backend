package life.catalogue.api.search;

import java.util.Objects;
import javax.ws.rs.QueryParam;

import org.gbif.nameparser.api.Rank;

public class EstimateSearchRequest {
  
  @QueryParam("id")
  private String id;

  @QueryParam("datasetKey")
  private Integer datasetKey;
  
  @QueryParam("rank")
  private Rank rank;
  
  @QueryParam("min")
  private Integer min;
  
  @QueryParam("max")
  private Integer max;

  @QueryParam("userKey")
  private Integer userKey;
  
  @QueryParam("broken")
  private boolean broken = false;
  
  public static EstimateSearchRequest byCatalogue(int datasetKey){
    EstimateSearchRequest req = new EstimateSearchRequest();
    req.datasetKey = datasetKey;
    return req;
  }
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public Rank getRank() {
    return rank;
  }
  
  public void setRank(Rank rank) {
    this.rank = rank;
  }
  
  public Integer getMin() {
    return min;
  }
  
  public void setMin(Integer min) {
    this.min = min;
  }
  
  public Integer getMax() {
    return max;
  }
  
  public void setMax(Integer max) {
    this.max = max;
  }
  
  public Integer getUserKey() {
    return userKey;
  }
  
  public void setUserKey(Integer userKey) {
    this.userKey = userKey;
  }
  
  public boolean isBroken() {
    return broken;
  }
  
  public void setBroken(boolean broken) {
    this.broken = broken;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EstimateSearchRequest that = (EstimateSearchRequest) o;
    return broken == that.broken &&
        Objects.equals(id, that.id) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        rank == that.rank &&
        Objects.equals(min, that.min) &&
        Objects.equals(max, that.max) &&
        Objects.equals(userKey, that.userKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, rank, min, max, userKey, broken);
  }
}
