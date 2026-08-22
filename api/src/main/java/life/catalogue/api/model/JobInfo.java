package life.catalogue.api.model;

import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The persisted, generic representation of a background job as stored in the job table.
 * One instance exists for every job of any kind that was ever submitted to the JobExecutor.
 * Job specific metrics, e.g. import or sync metrics, live in dedicated satellite tables
 * that link back to this record via their job_key column.
 */
public class JobInfo implements Entity<UUID> {

  private UUID key;
  private String job; // simple java class name of the job
  private JobStatus status;
  private String step;
  private JobPriority priority;
  private Integer datasetKey;
  private Integer sectorKey;
  private Integer createdBy;
  private LocalDateTime created;
  private LocalDateTime started;
  private LocalDateTime finished;
  private String error;
  private JsonNode params;
  private String resultMd5;
  private Long resultSize;
  private LocalDateTime resultDeleted;

  @Override
  public UUID getKey() {
    return key;
  }

  @Override
  public void setKey(UUID key) {
    this.key = key;
  }

  public String getJob() {
    return job;
  }

  public void setJob(String job) {
    this.job = job;
  }

  public JobStatus getStatus() {
    return status;
  }

  public void setStatus(JobStatus status) {
    this.status = status;
  }

  public String getStep() {
    return step;
  }

  public void setStep(String step) {
    this.step = step;
  }

  public JobPriority getPriority() {
    return priority;
  }

  public void setPriority(JobPriority priority) {
    this.priority = priority;
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

  public LocalDateTime getCreated() {
    return created;
  }

  public void setCreated(LocalDateTime created) {
    this.created = created;
  }

  public LocalDateTime getStarted() {
    return started;
  }

  public void setStarted(LocalDateTime started) {
    this.started = started;
  }

  public LocalDateTime getFinished() {
    return finished;
  }

  public void setFinished(LocalDateTime finished) {
    this.finished = finished;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public JsonNode getParams() {
    return params;
  }

  public void setParams(JsonNode params) {
    this.params = params;
  }

  public String getResultMd5() {
    return resultMd5;
  }

  public void setResultMd5(String resultMd5) {
    this.resultMd5 = resultMd5;
  }

  public Long getResultSize() {
    return resultSize;
  }

  public void setResultSize(Long resultSize) {
    this.resultSize = resultSize;
  }

  public LocalDateTime getResultDeleted() {
    return resultDeleted;
  }

  public void setResultDeleted(LocalDateTime resultDeleted) {
    this.resultDeleted = resultDeleted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JobInfo)) return false;
    JobInfo jobInfo = (JobInfo) o;
    return Objects.equals(key, jobInfo.key)
           && Objects.equals(job, jobInfo.job)
           && status == jobInfo.status
           && Objects.equals(step, jobInfo.step)
           && priority == jobInfo.priority
           && Objects.equals(datasetKey, jobInfo.datasetKey)
           && Objects.equals(sectorKey, jobInfo.sectorKey)
           && Objects.equals(createdBy, jobInfo.createdBy)
           && Objects.equals(created, jobInfo.created)
           && Objects.equals(started, jobInfo.started)
           && Objects.equals(finished, jobInfo.finished)
           && Objects.equals(error, jobInfo.error)
           && Objects.equals(params, jobInfo.params)
           && Objects.equals(resultMd5, jobInfo.resultMd5)
           && Objects.equals(resultSize, jobInfo.resultSize)
           && Objects.equals(resultDeleted, jobInfo.resultDeleted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, job, status, step, priority, datasetKey, sectorKey, createdBy,
      created, started, finished, error, params, resultMd5, resultSize, resultDeleted);
  }

  @Override
  public String toString() {
    return job + " " + key + ": " + status + (step == null ? "" : "/" + step);
  }
}
