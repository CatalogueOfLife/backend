package life.catalogue.matching.decision;

import life.catalogue.api.search.SectorSearchRequest;

import java.util.Objects;

public class SectorRematchRequest extends RematchRequest {
  private Integer subjectDatasetKey;
  private Boolean target;
  private Boolean subject;

  public SectorRematchRequest() {
  }

  public SectorRematchRequest(Integer id) {
    super(id);
  }

  public SectorRematchRequest(int datasetKey, boolean brokenOnly) {
    super(datasetKey, brokenOnly);
  }

  /**
   * A rematch request for all subjects regardless if broken or not. But not for targets
   * @param datasetKey
   * @param subjectDatasetKey
   */
  public SectorRematchRequest(int datasetKey, int subjectDatasetKey) {
    super(datasetKey, false);
    this.subjectDatasetKey = subjectDatasetKey;
    this.subject = true;
    this.target = false;
  }

  public SectorRematchRequest(int datasetKey, int subjectDatasetKey, boolean subject, boolean target) {
    super(datasetKey, false);
    this.subjectDatasetKey = subjectDatasetKey;
    this.subject = subject;
    this.target = target;
  }


  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }

  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }

  /**
   * @return true when the target should be rematched
   */
  public Boolean isTarget() {
    return target;
  }

  public void setTarget(boolean target) {
    this.target = target;
  }

  /**
   * @return true when the subject should be rematched
   */
  public Boolean isSubject() {
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
