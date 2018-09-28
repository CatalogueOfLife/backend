package org.col.api.model;

import java.time.LocalDateTime;
import java.util.Objects;

import org.col.api.vocab.TaxonomicStatus;

/**
 * A single, persisted editorial decision about a single name/taxon
 * within a given CoL sector.
 */
public class EditorialDecision {
  private Integer key;
  private Integer sectorKey;
  private NameRef subject;
  private TaxonomicStatus status;
  private String name;
  private String authorship;
  private LocalDateTime created;
  private LocalDateTime deleted;
  
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
  
  public TaxonomicStatus getStatus() {
    return status;
  }
  
  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
  public String getAuthorship() {
    return authorship;
  }
  
  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }
  
  public LocalDateTime getDeleted() {
    return deleted;
  }
  
  public void setDeleted(LocalDateTime deleted) {
    this.deleted = deleted;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EditorialDecision that = (EditorialDecision) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(sectorKey, that.sectorKey) &&
        Objects.equals(subject, that.subject) &&
        status == that.status &&
        Objects.equals(name, that.name) &&
        Objects.equals(authorship, that.authorship) &&
        Objects.equals(created, that.created) &&
        Objects.equals(deleted, that.deleted);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(key, sectorKey, subject, status, name, authorship, created, deleted);
  }
  
  @Override
  public String toString() {
    return "EditorialDecision{" + key + " on " + subject + '}';
  }
}
