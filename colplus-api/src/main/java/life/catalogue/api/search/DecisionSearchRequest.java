package life.catalogue.api.search;

import javax.ws.rs.QueryParam;

import org.gbif.nameparser.api.Rank;

public class DecisionSearchRequest {
  
  @QueryParam("id")
  private String id;

  @QueryParam("datasetKey")
  private Integer datasetKey;
  
  @QueryParam("subjectDatasetKey")
  private Integer subjectDatasetKey;
  
  @QueryParam("rank")
  private Rank rank;

  @QueryParam("userKey")
  private Integer userKey;
  
  @QueryParam("broken")
  private boolean broken = false;
  
  public static DecisionSearchRequest byCatalogue(int datasetKey){
    DecisionSearchRequest req = new DecisionSearchRequest();
    req.datasetKey = datasetKey;
    return req;
  }

  public static DecisionSearchRequest byDataset(int datasetKey, int subjectDatasetKey){
    DecisionSearchRequest req = byCatalogue(datasetKey);
    req.subjectDatasetKey = subjectDatasetKey;
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
  
  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }
  
  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }
  
  public Rank getRank() {
    return rank;
  }
  
  public void setRank(Rank rank) {
    this.rank = rank;
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
}
