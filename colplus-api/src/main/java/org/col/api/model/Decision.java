package org.col.api.model;

import java.time.LocalDateTime;
import java.util.Objects;

public abstract class Decision implements IntKey, UserManaged {
  protected Integer key;
  protected Integer colSourceKey;
  protected SimpleName subject;
  protected String note;
  
  private LocalDateTime created;
  private Integer createdBy;
  private LocalDateTime modified;
  private Integer modifiedBy;

  @Override
  public Integer getKey() {
    return key;
  }
  
  @Override
  public void setKey(Integer key) {
    this.key = key;
  }
  
  /**
   * The col source the subject originates from
   */
  public Integer getColSourceKey() {
    return colSourceKey;
  }
  
  public void setColSourceKey(Integer colSourceKey) {
    this.colSourceKey = colSourceKey;
  }
  
  public SimpleName getSubject() {
    return subject;
  }
  
  public void setSubject(SimpleName subject) {
    this.subject = subject;
  }
  
  public String getNote() {
    return note;
  }
  
  public void setNote(String note) {
    this.note = note;
  }
  
  @Override
  public LocalDateTime getCreated() {
    return created;
  }
  
  @Override
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }
  
  @Override
  public Integer getCreatedBy() {
    return createdBy;
  }
  
  @Override
  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }
  
  @Override
  public LocalDateTime getModified() {
    return modified;
  }
  
  @Override
  public void setModified(LocalDateTime modified) {
    this.modified = modified;
  }
  
  @Override
  public Integer getModifiedBy() {
    return modifiedBy;
  }
  
  @Override
  public void setModifiedBy(Integer modifiedBy) {
    this.modifiedBy = modifiedBy;
  }
  
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Decision decision = (Decision) o;
    return Objects.equals(key, decision.key) &&
        Objects.equals(colSourceKey, decision.colSourceKey) &&
        Objects.equals(subject, decision.subject) &&
        Objects.equals(note, decision.note) &&
        Objects.equals(created, decision.created) &&
        Objects.equals(createdBy, decision.createdBy) &&
        Objects.equals(modified, decision.modified) &&
        Objects.equals(modifiedBy, decision.modifiedBy);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(key, colSourceKey, subject, note, created, createdBy, modified, modifiedBy);
  }
}
