package life.catalogue.api.search;

import life.catalogue.api.model.Sector;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;

public class SectorSearchRequest extends BaseDecisionSearchRequest {

  @QueryParam("subjectDatasetKey")
  private Integer subjectDatasetKey;
  
  @QueryParam("lastSync")
  private LocalDate lastSync;

  @QueryParam("mode")
  private Set<Sector.Mode> mode;

  @QueryParam("subject")
  private boolean subject = false;

  @Min(0)
  @QueryParam("minSize")
  private Integer minSize;

  @QueryParam("withoutData")
  private boolean withoutData = false;

  @QueryParam("publisherKey")
  private UUID publisherKey;

  public static SectorSearchRequest byProject(int datasetKey){
    SectorSearchRequest req = new SectorSearchRequest();
    req.datasetKey = datasetKey;
    return req;
  }

  public static SectorSearchRequest byDataset(int datasetKey, int subjectDatasetKey){
    SectorSearchRequest req = byProject(datasetKey);
    req.subjectDatasetKey = subjectDatasetKey;
    return req;
  }

  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }

  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }

  public LocalDate getLastSync() {
    return lastSync;
  }

  public void setLastSync(LocalDate lastSync) {
    this.lastSync = lastSync;
  }

  public Set<Sector.Mode> getMode() {
    return mode;
  }

  public void setMode(Set<Sector.Mode> mode) {
    this.mode = mode;
  }

  public boolean isSubject() {
    return subject;
  }

  public void setSubject(boolean subject) {
    this.subject = subject;
  }

  public boolean isWithoutData() {
    return withoutData;
  }

  public void setWithoutData(boolean withoutData) {
    this.withoutData = withoutData;
  }

  public Integer getMinSize() {
    return minSize;
  }

  public void setMinSize(Integer minSize) {
    this.minSize = minSize;
  }

  public UUID getPublisherKey() {
    return publisherKey;
  }

  public void setPublisherKey(UUID publisherKey) {
    this.publisherKey = publisherKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SectorSearchRequest that = (SectorSearchRequest) o;
    return subject == that.subject && withoutData == that.withoutData && Objects.equals(subjectDatasetKey, that.subjectDatasetKey) && Objects.equals(lastSync, that.lastSync) && Objects.equals(mode, that.mode) && Objects.equals(minSize, that.minSize) && Objects.equals(publisherKey, that.publisherKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), subjectDatasetKey, lastSync, mode, subject, minSize, withoutData, publisherKey);
  }
}
