package life.catalogue.api.search;

import life.catalogue.api.model.Sector;

import javax.ws.rs.QueryParam;
import java.time.LocalDate;
import java.util.Objects;

public class SectorSearchRequest extends BaseDecisionSearchRequest {

  @QueryParam("subjectDatasetKey")
  private Integer subjectDatasetKey;
  
  @QueryParam("lastSync")
  private LocalDate lastSync;

  @QueryParam("mode")
  private Sector.Mode mode;

  @QueryParam("subject")
  private boolean subject = false;

  @QueryParam("withoutData")
  private boolean withoutData = false;

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

  public Sector.Mode getMode() {
    return mode;
  }

  public void setMode(Sector.Mode mode) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorSearchRequest)) return false;
    SectorSearchRequest that = (SectorSearchRequest) o;
    return subject == that.subject &&
      Objects.equals(subjectDatasetKey, that.subjectDatasetKey) &&
      Objects.equals(lastSync, that.lastSync) &&
      mode == that.mode &&
      withoutData == that.withoutData;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, subjectDatasetKey, lastSync, mode, subject, withoutData);
  }
}
