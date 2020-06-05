package life.catalogue.match;

import life.catalogue.api.search.SectorSearchRequest;

import java.util.Objects;

public class SectorRematchRequest extends RematchRequest {
  private Integer subjectDatasetKey;
  private boolean matchTarget = false;
  private boolean matchSubject = true;

  public SectorRematchRequest() {
  }

  public SectorRematchRequest(Integer id) {
    super(id);
  }

  public SectorRematchRequest(int datasetKey, boolean brokenOnly) {
    super(datasetKey, brokenOnly);
  }

  public SectorRematchRequest(int datasetKey, int subjectDatasetKey, boolean brokenOnly) {
    super(datasetKey, brokenOnly);
    this.subjectDatasetKey = subjectDatasetKey;
  }


  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }

  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }

  public boolean isMatchTarget() {
    return matchTarget;
  }

  public void setMatchTarget(boolean matchTarget) {
    this.matchTarget = matchTarget;
  }

  public boolean isMatchSubject() {
    return matchSubject;
  }

  public void setMatchSubject(boolean matchSubject) {
    this.matchSubject = matchSubject;
  }

  public SectorSearchRequest buildSearchRequest() {
    SectorSearchRequest search = SectorSearchRequest.byProject(getDatasetKey());
    search.setBroken(isBrokenOnly());
    if (getSubjectDatasetKey() != null) {
      search.setSubjectDatasetKey(getSubjectDatasetKey());
    }
    return search;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorRematchRequest)) return false;
    if (!super.equals(o)) return false;
    SectorRematchRequest that = (SectorRematchRequest) o;
    return matchTarget == that.matchTarget &&
      matchSubject == that.matchSubject &&
      Objects.equals(subjectDatasetKey, that.subjectDatasetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), subjectDatasetKey, matchTarget, matchSubject);
  }
}
