package life.catalogue.api.search;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.JobStatus;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.print.attribute.standard.JobPriority;
import jakarta.ws.rs.QueryParam;

public class JobSearchRequest {

  @QueryParam("key")
  private UUID key; // unsupported so far

  /**
   * Filter by dataset.
   */
  @QueryParam("datasetKey")
  private Integer datasetKey;

  /**
   * Filters jobs by datasets that contribute to a given project.
   */
  @QueryParam("contributesTo")
  private Integer contributesTo; // unsupported so far

  /**
   * Filter jobs by the user that has created it.
   */
  @QueryParam("createdBy")
  private Integer createdBy;

  /**
   * Filter by status.
   */
  @QueryParam("status")
  private JobStatus status; // unsupported so far

  /**
   * import state.
   * Similar to job status, but until dataset imports are not migrated to the background job infrastructure this has to remain.
   */
  @QueryParam("state")
  private Set<ImportState> states;

  /**
   * Filter by priority.
   */
  @QueryParam("priority")
  private JobPriority priority; // unsupported so far

  /**
   * Filter by job (class) name.
   */
  @QueryParam("job")
  private String job;

  /**
   * Filter by source archive format.
   */
  @QueryParam("format")
  private DataFormat format;


  public UUID getKey() {
    return key;
  }

  public void setKey(UUID key) {
    this.key = key;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Integer getContributesTo() {
    return contributesTo;
  }

  public void setContributesTo(Integer contributesTo) {
    this.contributesTo = contributesTo;
  }

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }

  public JobStatus getStatus() {
    return status;
  }

  public void setStatus(JobStatus status) {
    this.status = status;
  }

  public JobPriority getPriority() {
    return priority;
  }

  public void setPriority(JobPriority priority) {
    this.priority = priority;
  }

  public String getJob() {
    return job;
  }

  public void setJob(String job) {
    this.job = job;
  }

  public Set<ImportState> getStates() {
    return states;
  }

  public void setStates(Set<ImportState> states) {
    this.states = states;
  }

  public DataFormat getFormat() {
    return format;
  }

  public void setFormat(DataFormat format) {
    this.format = format;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JobSearchRequest)) return false;
    JobSearchRequest that = (JobSearchRequest) o;
    return Objects.equals(key, that.key)
           && Objects.equals(datasetKey, that.datasetKey)
           && Objects.equals(contributesTo, that.contributesTo)
           && Objects.equals(createdBy, that.createdBy)
           && status == that.status
           && Objects.equals(states, that.states)
           && Objects.equals(priority, that.priority)
           && Objects.equals(job, that.job)
           && format == that.format;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, datasetKey, contributesTo, createdBy, status, states, priority, job, format);
  }
}
