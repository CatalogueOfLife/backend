package life.catalogue.api.search;

import org.gbif.nameparser.api.Rank;

import javax.ws.rs.QueryParam;
import java.util.Objects;

public class BaseDecisionSearchRequest {

  @QueryParam("id")
  protected String id;

  protected Integer datasetKey;

  @QueryParam("name")
  protected String name;

  @QueryParam("rank")
  protected Rank rank;

  @QueryParam("modifiedBy")
  protected Integer modifiedBy;

  @QueryParam("broken")
  protected boolean broken = false;


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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
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
    if (!(o instanceof BaseDecisionSearchRequest)) return false;
    BaseDecisionSearchRequest that = (BaseDecisionSearchRequest) o;
    return broken == that.broken &&
      Objects.equals(id, that.id) &&
      Objects.equals(datasetKey, that.datasetKey) &&
      Objects.equals(name, that.name) &&
      rank == that.rank &&
      Objects.equals(modifiedBy, that.modifiedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, name, rank, modifiedBy, broken);
  }
}
