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

  @QueryParam("nested")
  private boolean nested = false;

  @Min(0)
  @QueryParam("minSize")
  private Integer minSize;

  @QueryParam("withoutData")
  private boolean withoutData = false;

  // restrict to sectors that have a subject id which points to a different name in the source than what is configured in the subject_name of the sector.
  @QueryParam("wrongSubject")
  private boolean wrongSubject = false;

  @QueryParam("publisherKey")
  private UUID publisherKey;

  // restrict to sectors that have metadata of their own, so a source page can list just the sectors
  // that describe themselves. See issue #1273.
  @QueryParam("hasMetadata")
  private Boolean hasMetadata;

  // the source's own sector whose metadata a sector absorbs
  @QueryParam("subjectSectorId")
  private Integer subjectSectorId;

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

  public boolean isNested() {
    return nested;
  }

  public void setNested(boolean nested) {
    this.nested = nested;
  }

  public boolean isWrongSubject() {
    return wrongSubject;
  }

  public void setWrongSubject(boolean wrongSubject) {
    this.wrongSubject = wrongSubject;
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

  public Boolean getHasMetadata() {
    return hasMetadata;
  }

  public void setHasMetadata(Boolean hasMetadata) {
    this.hasMetadata = hasMetadata;
  }

  public Integer getSubjectSectorId() {
    return subjectSectorId;
  }

  public void setSubjectSectorId(Integer subjectSectorId) {
    this.subjectSectorId = subjectSectorId;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SectorSearchRequest that = (SectorSearchRequest) o;
    return subject == that.subject && nested == that.nested && withoutData == that.withoutData && wrongSubject == that.wrongSubject && Objects.equals(subjectDatasetKey, that.subjectDatasetKey) && Objects.equals(lastSync, that.lastSync) && Objects.equals(mode, that.mode) && Objects.equals(minSize, that.minSize) && Objects.equals(publisherKey, that.publisherKey) && Objects.equals(hasMetadata, that.hasMetadata) && Objects.equals(subjectSectorId, that.subjectSectorId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), subjectDatasetKey, lastSync, mode, subject, nested, minSize, withoutData, wrongSubject, publisherKey, hasMetadata, subjectSectorId);
  }
}
