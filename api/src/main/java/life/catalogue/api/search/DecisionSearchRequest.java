package life.catalogue.api.search;

import life.catalogue.api.model.EditorialDecision;

import java.util.Objects;

import javax.ws.rs.QueryParam;

public class DecisionSearchRequest extends BaseDecisionSearchRequest {

  @QueryParam("subjectDatasetKey")
  private Integer subjectDatasetKey;

  @QueryParam("mode")
  private EditorialDecision.Mode mode;

  @QueryParam("subject")
  private boolean subject = false;

  public static DecisionSearchRequest byProject(int datasetKey){
    DecisionSearchRequest req = new DecisionSearchRequest();
    req.datasetKey = datasetKey;
    return req;
  }

  public static DecisionSearchRequest byDataset(int subjectDatasetKey){
    DecisionSearchRequest req = new DecisionSearchRequest();
    req.subjectDatasetKey = subjectDatasetKey;
    return req;
  }

  public static DecisionSearchRequest byDataset(int datasetKey, int subjectDatasetKey){
    DecisionSearchRequest req = byProject(datasetKey);
    req.subjectDatasetKey = subjectDatasetKey;
    return req;
  }

  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }
  
  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }
  
  public EditorialDecision.Mode getMode() {
    return mode;
  }
  
  public void setMode(EditorialDecision.Mode mode) {
    this.mode = mode;
  }

  public boolean isSubject() {
    return subject;
  }

  public void setSubject(boolean subject) {
    this.subject = subject;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DecisionSearchRequest)) return false;
    DecisionSearchRequest that = (DecisionSearchRequest) o;
    return subject == that.subject &&
      Objects.equals(subjectDatasetKey, that.subjectDatasetKey) &&
      mode == that.mode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(subjectDatasetKey, mode, subject);
  }
}
