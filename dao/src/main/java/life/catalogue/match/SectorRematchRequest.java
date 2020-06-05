package life.catalogue.match;

import life.catalogue.api.search.SectorSearchRequest;

import java.util.Objects;

public class SectorRematchRequest extends RematchRequest {
  private Integer subjectDatasetKey;
  private boolean target = false;
  private boolean subject = true;

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

  public boolean isTarget() {
    return target;
  }

  public void setTarget(boolean target) {
    this.target = target;
  }

  public boolean isSubject() {
    return subject;
  }

  public void setSubject(boolean subject) {
    this.subject = subject;
  }

  public SectorSearchRequest buildSearchRequest() {
    SectorSearchRequest search = SectorSearchRequest.byProject(getDatasetKey());
    search.setBroken(isBroken());
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
    return target == that.target &&
      subject == that.subject &&
      Objects.equals(subjectDatasetKey, that.subjectDatasetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), subjectDatasetKey, target, subject);
  }
}
