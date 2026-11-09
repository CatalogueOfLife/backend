package life.catalogue.api.model;

import life.catalogue.api.vocab.OutboxStatus;
import life.catalogue.api.vocab.OutboxType;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A durable command in the outbox table: something that has to happen exactly once and must survive restarts and
 * deploys, like a DataCite call or a sector deletion. Producers write it in the transaction of the change that
 * caused it, a single runner on the active app executes and then deletes it.
 */
public class OutboxEntry {
  private Long id;
  private OutboxType type;
  private String key;
  private JsonNode params;
  private OutboxStatus status;
  private LocalDateTime due;
  private int version;
  private int attempts;
  private String lastError;
  private String claimedBy;
  private Integer createdBy;
  private LocalDateTime created;
  private LocalDateTime modified;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public OutboxType getType() {
    return type;
  }

  public void setType(OutboxType type) {
    this.type = type;
  }

  /**
   * The dedupe key within the type: a request for a type and key that is already waiting is merged into it.
   */
  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public JsonNode getParams() {
    return params;
  }

  public void setParams(JsonNode params) {
    this.params = params;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public void setStatus(OutboxStatus status) {
    this.status = status;
  }

  public LocalDateTime getDue() {
    return due;
  }

  public void setDue(LocalDateTime due) {
    this.due = due;
  }

  /**
   * Bumped by every merge, so a run only completes the request it actually read.
   */
  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public String getClaimedBy() {
    return claimedBy;
  }

  public void setClaimedBy(String claimedBy) {
    this.claimedBy = claimedBy;
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

  public LocalDateTime getModified() {
    return modified;
  }

  public void setModified(LocalDateTime modified) {
    this.modified = modified;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof OutboxEntry that)) return false;
    return version == that.version
      && attempts == that.attempts
      && Objects.equals(id, that.id)
      && type == that.type
      && Objects.equals(key, that.key)
      && Objects.equals(params, that.params)
      && status == that.status
      && Objects.equals(due, that.due)
      && Objects.equals(lastError, that.lastError)
      && Objects.equals(claimedBy, that.claimedBy)
      && Objects.equals(createdBy, that.createdBy)
      && Objects.equals(created, that.created)
      && Objects.equals(modified, that.modified);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, key, params, status, due, version, attempts, lastError, claimedBy, createdBy, created, modified);
  }

  @Override
  public String toString() {
    return type + ":" + key + " #" + id + " " + status + " v" + version + (attempts > 0 ? " attempts=" + attempts : "") + " " + params;
  }
}
