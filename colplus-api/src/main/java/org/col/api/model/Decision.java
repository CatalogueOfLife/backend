package org.col.api.model;

import java.time.LocalDateTime;
import java.util.Objects;

abstract class Decision implements IntKey, CreatedModified {
  protected Integer key;
  protected NameRef subject;
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
  
  public NameRef getSubject() {
    return subject;
  }
  
  public void setSubject(NameRef subject) {
    this.subject = subject;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Decision decision = (Decision) o;
    return createdBy == decision.createdBy &&
        modifiedBy == decision.modifiedBy &&
        Objects.equals(subject, decision.subject) &&
        Objects.equals(key, decision.key) &&
        Objects.equals(note, decision.note) &&
        Objects.equals(created, decision.created) &&
        Objects.equals(modified, decision.modified);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(subject, key, note, created, createdBy, modified, modifiedBy);
  }
}
