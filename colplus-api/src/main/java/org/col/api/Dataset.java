package org.col.api;

import org.col.api.vocab.DataFormat;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 *
 */
public class Dataset {
  private Integer key;
  private String alias;
  @NotEmpty
  private String title;
  private UUID gbifKey;
  private String description;
  private String groupName;
  private String authorsAndEditors;
  private String organisation;
  private String contactPerson;
  private String version;
  private LocalDate releaseDate;
  private String taxonomicCoverage;
  private String coverage;
  @Max(100)
  @Min(0)
  private Integer completeness;
  @Max(5)
  @Min(1)
  private Integer confidence;
  private URI homepage;
  private DataFormat dataFormat;
  private URI dataAccess;
  private String notes;
  private LocalDateTime created;
  private LocalDateTime modified;
  private LocalDateTime deleted;

  public Dataset() {
    this.key = key;
  }

  /**
   * A key only dataset often used to represent just a foreign key to a dataset in other classes.
   */
  public Dataset(Integer key) {
    this.key = key;
  }

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
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

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getAuthorsAndEditors() {
    return authorsAndEditors;
  }

  public void setAuthorsAndEditors(String authorsAndEditors) {
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

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public LocalDate getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(LocalDate releaseDate) {
    this.releaseDate = releaseDate;
  }

  public String getTaxonomicCoverage() {
    return taxonomicCoverage;
  }

  public void setTaxonomicCoverage(String taxonomicCoverage) {
    this.taxonomicCoverage = taxonomicCoverage;
  }

  public String getCoverage() {
    return coverage;
  }

  public void setCoverage(String coverage) {
    this.coverage = coverage;
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

  public URI getHomepage() {
    return homepage;
  }

  public void setHomepage(URI homepage) {
    this.homepage = homepage;
  }

  public DataFormat getDataFormat() {
    return dataFormat;
  }

  public void setDataFormat(DataFormat dataFormat) {
    this.dataFormat = dataFormat;
  }

  public URI getDataAccess() {
    return dataAccess;
  }

  public void setDataAccess(URI dataAccess) {
    this.dataAccess = dataAccess;
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
    Dataset dataset = (Dataset) o;
    return Objects.equals(key, dataset.key) &&
        Objects.equals(alias, dataset.alias) &&
        Objects.equals(title, dataset.title) &&
        Objects.equals(gbifKey, dataset.gbifKey) &&
        Objects.equals(description, dataset.description) &&
        Objects.equals(groupName, dataset.groupName) &&
        Objects.equals(authorsAndEditors, dataset.authorsAndEditors) &&
        Objects.equals(organisation, dataset.organisation) &&
        Objects.equals(contactPerson, dataset.contactPerson) &&
        Objects.equals(version, dataset.version) &&
        Objects.equals(releaseDate, dataset.releaseDate) &&
        Objects.equals(taxonomicCoverage, dataset.taxonomicCoverage) &&
        Objects.equals(coverage, dataset.coverage) &&
        Objects.equals(completeness, dataset.completeness) &&
        Objects.equals(confidence, dataset.confidence) &&
        Objects.equals(homepage, dataset.homepage) &&
        Objects.equals(dataFormat, dataset.dataFormat) &&
        Objects.equals(dataAccess, dataset.dataAccess) &&
        Objects.equals(notes, dataset.notes) &&
        Objects.equals(created, dataset.created) &&
        Objects.equals(modified, dataset.modified) &&
        Objects.equals(deleted, dataset.deleted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, alias, title, gbifKey, description, groupName, authorsAndEditors, organisation, contactPerson, version, releaseDate, taxonomicCoverage, coverage, completeness, confidence, homepage, dataFormat, dataAccess, notes, created, modified, deleted);
  }

  @Override
  public String toString() {
    return "Dataset " + key + ": " + title;
  }
}
