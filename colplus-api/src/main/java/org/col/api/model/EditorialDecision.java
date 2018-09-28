package org.col.api.model;

import java.time.LocalDateTime;

import org.col.api.vocab.TaxonomicStatus;

/**
 * A single, persisted editorial decision about a single name/taxon
 * within a given CoL sector.
 */
public class EditorialDecision {
	private Integer key;
  private Integer sectorKey;
  private NameRef subject;
  private TaxonomicStatus statusChange;
  private String nameChange;
  private String authorshipChange;
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

  public TaxonomicStatus getStatusChange() {
    return statusChange;
  }

  public void setStatusChange(TaxonomicStatus statusChange) {
    this.statusChange = statusChange;
  }

  public String getNameChange() {
    return nameChange;
  }

  public void setNameChange(String nameChange) {
    this.nameChange = nameChange;
  }

  public String getAuthorshipChange() {
    return authorshipChange;
  }

  public void setAuthorshipChange(String authorshipChange) {
    this.authorshipChange = authorshipChange;
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
}
