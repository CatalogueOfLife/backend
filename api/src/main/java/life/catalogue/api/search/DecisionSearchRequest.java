package life.catalogue.api.search;

import life.catalogue.api.model.EditorialDecision;

import java.util.Objects;
import java.util.Set;

import jakarta.ws.rs.QueryParam;

public class DecisionSearchRequest extends BaseDecisionSearchRequest {

  @QueryParam("subjectDatasetKey")
  private Integer subjectDatasetKey;

  @QueryParam("mode")
  private EditorialDecision.Mode mode;

  @QueryParam("subject")
  private boolean subject = false;

  @QueryParam("facet")
  private Set<String> facets;

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

  public Set<String> getFacets() {
    return facets;
  }

  public void setFacets(Set<String> facets) {
    this.facets = facets;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DecisionSearchRequest)) return false;
    if (!super.equals(o)) return false;
    DecisionSearchRequest that = (DecisionSearchRequest) o;
    return subject == that.subject && Objects.equals(subjectDatasetKey, that.subjectDatasetKey) && mode == that.mode && Objects.equals(facets, that.facets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), subjectDatasetKey, mode, subject, facets);
  }
}
