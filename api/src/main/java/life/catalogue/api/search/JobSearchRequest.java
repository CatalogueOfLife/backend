package life.catalogue.api.search;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
   * Filter by one or more job statuses.
   */
  @QueryParam("status")
  private Set<JobStatus> status;

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

  public Set<JobStatus> getStatus() {
    return status;
  }

  public void setStatus(Set<JobStatus> status) {
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
           && Objects.equals(status, that.status)
           && Objects.equals(priority, that.priority)
           && Objects.equals(job, that.job)
           && format == that.format;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, datasetKey, contributesTo, createdBy, status, priority, job, format);
  }
}
