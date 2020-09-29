package life.catalogue.matching.decision;

import life.catalogue.api.search.DecisionSearchRequest;

import java.util.Objects;

public class DecisionRematchRequest extends RematchRequest {
  private Integer subjectDatasetKey;

  public DecisionRematchRequest() {
  }

  public DecisionRematchRequest(Integer id) {
    super(id);
  }

  public DecisionRematchRequest(int datasetKey, boolean brokenOnly) {
    super(datasetKey, brokenOnly);
  }

  public DecisionRematchRequest(int datasetKey, int subjectDatasetKey, boolean brokenOnly) {
    super(datasetKey, brokenOnly);
    this.subjectDatasetKey = subjectDatasetKey;
  }


  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }

  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }

  public DecisionSearchRequest buildSearchRequest() {
    DecisionSearchRequest search = DecisionSearchRequest.byProject(getDatasetKey());
    search.setBroken(isBroken());
    if (getSubjectDatasetKey() != null) {
      search.setSubjectDatasetKey(getSubjectDatasetKey());
    }
    return search;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DecisionRematchRequest)) return false;
    if (!super.equals(o)) return false;
    DecisionRematchRequest that = (DecisionRematchRequest) o;
    return Objects.equals(subjectDatasetKey, that.subjectDatasetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), subjectDatasetKey);
  }
}
