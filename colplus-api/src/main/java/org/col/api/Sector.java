package org.col.api;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A taxonomic sector definition within a dataset that is used to assemble the Catalogue of Life.
 * TODO: add definition of root taxa and optional exclusion filter (by rank, name or group) - will also serve to show the taxonomic coverage in the CoL AC
 *
 */
public class Sector {
	private Integer key;
  private Integer datasetKey;
  /**
   * English name for the group defined by this sector
   */
	private String groupName;
  private String alias;
	private String title;
	private String description;
	private List<String> authorsAndEditors;
	private String organisation;
	private String contactPerson;
  private String coverage;
	@Max(100)
	@Min(0)
	private Integer completeness;
	@Max(5)
	@Min(1)
	private Integer confidence;
	private String notes;
	private LocalDateTime created;
	private LocalDateTime modified;
	private LocalDateTime deleted;

	public Sector() {
	}

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getAuthorsAndEditors() {
    return authorsAndEditors;
  }

  public void setAuthorsAndEditors(List<String> authorsAndEditors) {
    this.authorsAndEditors = authorsAndEditors;
  }

  public String getOrganisation() {
    return organisation;
  }

  public void setOrganisation(String organisation) {
    this.organisation = organisation;
  }

  public String getContactPerson() {
    return contactPerson;
  }

  public void setContactPerson(String contactPerson) {
    this.contactPerson = contactPerson;
  }

  public Integer getCompleteness() {
    return completeness;
  }

  public void setCompleteness(Integer completeness) {
    this.completeness = completeness;
  }

  public Integer getConfidence() {
    return confidence;
  }

  public void setConfidence(Integer confidence) {
    this.confidence = confidence;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
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
    Sector sector = (Sector) o;
    return Objects.equals(key, sector.key) &&
        Objects.equals(datasetKey, sector.datasetKey) &&
        Objects.equals(groupName, sector.groupName) &&
        Objects.equals(alias, sector.alias) &&
        Objects.equals(title, sector.title) &&
        Objects.equals(description, sector.description) &&
        Objects.equals(authorsAndEditors, sector.authorsAndEditors) &&
        Objects.equals(organisation, sector.organisation) &&
        Objects.equals(contactPerson, sector.contactPerson) &&
        Objects.equals(completeness, sector.completeness) &&
        Objects.equals(confidence, sector.confidence) &&
        Objects.equals(notes, sector.notes) &&
        Objects.equals(created, sector.created) &&
        Objects.equals(modified, sector.modified) &&
        Objects.equals(deleted, sector.deleted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, datasetKey, groupName, alias, title, description, authorsAndEditors, organisation, contactPerson, completeness, confidence, notes, created, modified, deleted);
  }
}
