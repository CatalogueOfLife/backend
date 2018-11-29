package org.col.api.model;

import java.time.LocalDateTime;
import java.util.Objects;

import org.col.api.vocab.TaxonomicStatus;

/**
 * A single, persisted editorial decision about a single name/taxon
 * within a given CoL sector.
 *
 * A decision can change a record or block it and all its descendants entirely.
 * If blocked all further configured changes are ignored.
 * Otherwise all non null values from status or name should be applied to the underlying subject.
 */
public class EditorialDecision implements IntKey, CreatedModified {
  private Integer key;
  private Integer sectorKey;
  private NameRef subject;
  private boolean blocked;
  private TaxonomicStatus status;
  private Name name;
  private String note;
  private LocalDateTime created;
  private int createdBy;
  private LocalDateTime modified;
  private int modifiedBy;
  
  /**
   * Primary key
   */
  public Integer getKey() {
    return key;
  }
  
  public void setKey(Integer key) {
    this.key = key;
  }
  
  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public NameRef getSubject() {
    return subject;
  }
  
  public void setSubject(NameRef subject) {
    this.subject = subject;
  }
  
  public boolean isBlocked() {
    return blocked;
  }
  
  public void setBlocked(boolean blocked) {
    this.blocked = blocked;
  }
  
  public TaxonomicStatus getStatus() {
    return status;
  }
  
  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }
  
  @Override
  public LocalDateTime getCreated() {
    return created;
  }
  
  @Override
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }
  
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  public String getNote() {
    return note;
  }
  
  public void setNote(String note) {
    this.note = note;
  }
  
  @Override
  public int getCreatedBy() {
    return createdBy;
  }
  
  @Override
  public void setCreatedBy(int createdBy) {
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
  public int getModifiedBy() {
    return modifiedBy;
  }
  
  @Override
  public void setModifiedBy(int modifiedBy) {
    this.modifiedBy = modifiedBy;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EditorialDecision that = (EditorialDecision) o;
    return blocked == that.blocked &&
        createdBy == that.createdBy &&
        modifiedBy == that.modifiedBy &&
        Objects.equals(key, that.key) &&
        Objects.equals(sectorKey, that.sectorKey) &&
        Objects.equals(subject, that.subject) &&
        status == that.status &&
        Objects.equals(name, that.name) &&
        Objects.equals(note, that.note) &&
        Objects.equals(created, that.created) &&
        Objects.equals(modified, that.modified);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, sectorKey, subject, blocked, status, name, note, created, createdBy, modified, modifiedBy);
  }
  
  @Override
  public String toString() {
    return "EditorialDecision{" + key + " on " + subject + '}';
  }
}
