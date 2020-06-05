package life.catalogue.api.search;

import org.gbif.nameparser.api.Rank;

import javax.ws.rs.QueryParam;
import java.util.Objects;

public class EstimateSearchRequest {
  
  @QueryParam("id")
  private String id;

  private Integer datasetKey;
  
  @QueryParam("rank")
  private Rank rank;
  
  @QueryParam("min")
  private Integer min;
  
  @QueryParam("max")
  private Integer max;

  @QueryParam("modifiedBy")
  private Integer modifiedBy;

  @QueryParam("broken")
  private boolean broken = false;
  
  public static EstimateSearchRequest byProject(int datasetKey){
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
  
  public Integer getModifiedBy() {
    return modifiedBy;
  }
  
  public void setModifiedBy(Integer modifiedBy) {
    this.modifiedBy = modifiedBy;
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
        Objects.equals(modifiedBy, that.modifiedBy);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, rank, min, max, modifiedBy, broken);
  }
}
