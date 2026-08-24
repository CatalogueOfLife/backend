package life.catalogue.api.search;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobLane;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.ws.rs.QueryParam;

/**
 * Search request for the generic job history.
 * Also used to search the dataset import history, which supports the same filters
 * plus the import specific source archive format.
 */
public class JobSearchRequest {

  /**
   * Filter by a single job key.
   */
  @QueryParam("key")
  private UUID key;

  /**
   * Filter by dataset.
   */
  @QueryParam("datasetKey")
  private Integer datasetKey;

  /**
   * Filter by the sector a job was run for. Only sync jobs carry one.
   */
  @QueryParam("sectorKey")
  private Integer sectorKey;

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
   * Filter by one or more executor lanes.
   * Sector syncs make up the vast majority of all job records, so filtering them out
   * via lane is the cheapest way to get a useful history.
   */
  @QueryParam("lane")
  private Set<JobLane> lane;

  /**
   * Filter by priority.
   */
  @QueryParam("priority")
  private JobPriority priority;

  /**
   * Filter by one or more job (class) names, compared case insensitively.
   * Repeat the parameter to combine several, e.g. job=ProjectRelease&amp;job=XRelease.
   */
  @QueryParam("job")
  private Set<String> job;

  /**
   * Filter by source archive format. Only supported by the dataset import search.
   */
  @QueryParam("format")
  private DataFormat format;

  /**
   * Only include jobs created at or after this time. A bare date is read as the start of that day.
   */
  @QueryParam("createdAfter")
  private LocalDateTime createdAfter;

  /**
   * Only include jobs created at or before this time. A bare date is read as the start of that day,
   * so pass the following day to include a full day.
   */
  @QueryParam("createdBefore")
  private LocalDateTime createdBefore;


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

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
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

  public Set<JobLane> getLane() {
    return lane;
  }

  public void setLane(Set<JobLane> lane) {
    this.lane = lane;
  }

  public JobPriority getPriority() {
    return priority;
  }

  public void setPriority(JobPriority priority) {
    this.priority = priority;
  }

  public Set<String> getJob() {
    return job;
  }

  public void setJob(Set<String> job) {
    this.job = job;
  }

  public DataFormat getFormat() {
    return format;
  }

  public void setFormat(DataFormat format) {
    this.format = format;
  }

  public LocalDateTime getCreatedAfter() {
    return createdAfter;
  }

  public void setCreatedAfter(LocalDateTime createdAfter) {
    this.createdAfter = createdAfter;
  }

  public LocalDateTime getCreatedBefore() {
    return createdBefore;
  }

  public void setCreatedBefore(LocalDateTime createdBefore) {
    this.createdBefore = createdBefore;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JobSearchRequest)) return false;
    JobSearchRequest that = (JobSearchRequest) o;
    return Objects.equals(key, that.key)
           && Objects.equals(datasetKey, that.datasetKey)
           && Objects.equals(sectorKey, that.sectorKey)
           && Objects.equals(createdBy, that.createdBy)
           && Objects.equals(status, that.status)
           && Objects.equals(lane, that.lane)
           && priority == that.priority
           && Objects.equals(job, that.job)
           && format == that.format
           && Objects.equals(createdAfter, that.createdAfter)
           && Objects.equals(createdBefore, that.createdBefore);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, datasetKey, sectorKey, createdBy, status, lane, priority, job, format,
      createdAfter, createdBefore);
  }
}
