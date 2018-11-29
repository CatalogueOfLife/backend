package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.google.common.collect.Lists;
import org.col.api.constraints.AbsoluteURI;
import org.col.api.constraints.NotBlank;
import org.col.api.vocab.DatasetType;

/**
 * A citable source for a CoL data provider
 */
public class ColSource implements SourceMetadata, IntKey {
  private Integer key;
  @NotNull
  private Integer datasetKey;
  private String title;
  @NotBlank
  private String alias;
  private String description;
  private List<String> organisations = Lists.newArrayList();
  private String contactPerson;
  private List<String> authorsAndEditors = Lists.newArrayList();
  private String version;
  private LocalDate released;
  @AbsoluteURI
  private URI website;
  private String group;
  private DatasetType coverage;
  private String citation;
  private Integer livingSpeciesCount;
  private Integer livingInfraspecificCount;
  private Integer extinctSpeciesCount;
  private Integer extinctInfraspecificCount;
  private Integer synonymsCount;
  private Integer vernacularsCount;
  private Integer namesCount;
  private LocalDateTime created;
  @Max(5)
  @Min(1)
  private Integer confidence;
  @Max(100)
  @Min(0)
  private Integer completeness;
  private String notes;
  
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
  
  @Override
  public String getTitle() {
    return title;
  }
  
  @Override
  public void setTitle(String title) {
    this.title = title;
  }
  
  public String getAlias() {
    return alias;
  }
  
  public void setAlias(String alias) {
    this.alias = alias;
  }
  
  @Override
  public String getDescription() {
    return description;
  }
  
  @Override
  public void setDescription(String description) {
    this.description = description;
  }
  
  public List<String> getOrganisations() {
    return organisations;
  }
  
  public void setOrganisations(List<String> organisations) {
    this.organisations = organisations;
  }
  
  @Override
  public String getContact() {
    return contactPerson;
  }
  
  @Override
  public void setContact(String contact) {
    this.contactPerson = contact;
  }
  
  @Override
  public List<String> getAuthorsAndEditors() {
    return authorsAndEditors;
  }
  
  @Override
  public void setAuthorsAndEditors(List<String> authorsAndEditors) {
    this.authorsAndEditors = authorsAndEditors;
  }
  
  @Override
  public String getVersion() {
    return version;
  }
  
  @Override
  public void setVersion(String version) {
    this.version = version;
  }
  
  @Override
  public LocalDate getReleased() {
    return released;
  }
  
  @Override
  public void setReleased(LocalDate released) {
    this.released = released;
  }
  
  @Override
  public URI getWebsite() {
    return website;
  }
  
  @Override
  public void setWebsite(URI website) {
    this.website = website;
  }
  
  /**
   * English name for the taxonomic group dealt by this source
   */
  public String getGroup() {
    return group;
  }
  
  public void setGroup(String group) {
    this.group = group;
  }
  
  public DatasetType getCoverage() {
    return coverage;
  }
  
  public void setCoverage(DatasetType coverage) {
    this.coverage = coverage;
  }
  
  @Override
  public String getCitation() {
    return citation;
  }
  
  @Override
  public void setCitation(String citation) {
    this.citation = citation;
  }
  
  public Integer getLivingSpeciesCount() {
    return livingSpeciesCount;
  }
  
  public void setLivingSpeciesCount(Integer livingSpeciesCount) {
    this.livingSpeciesCount = livingSpeciesCount;
  }
  
  public Integer getLivingInfraspecificCount() {
    return livingInfraspecificCount;
  }
  
  public void setLivingInfraspecificCount(Integer livingInfraspecificCount) {
    this.livingInfraspecificCount = livingInfraspecificCount;
  }
  
  public Integer getExtinctSpeciesCount() {
    return extinctSpeciesCount;
  }
  
  public void setExtinctSpeciesCount(Integer extinctSpeciesCount) {
    this.extinctSpeciesCount = extinctSpeciesCount;
  }
  
  public Integer getExtinctInfraspecificCount() {
    return extinctInfraspecificCount;
  }
  
  public void setExtinctInfraspecificCount(Integer extinctInfraspecificCount) {
    this.extinctInfraspecificCount = extinctInfraspecificCount;
  }
  
  public Integer getSynonymsCount() {
    return synonymsCount;
  }
  
  public void setSynonymsCount(Integer synonymsCount) {
    this.synonymsCount = synonymsCount;
  }
  
  public Integer getVernacularsCount() {
    return vernacularsCount;
  }
  
  public void setVernacularsCount(Integer vernacularsCount) {
    this.vernacularsCount = vernacularsCount;
  }
  
  public Integer getNamesCount() {
    return namesCount;
  }
  
  public void setNamesCount(Integer namesCount) {
    this.namesCount = namesCount;
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public void setCreated(LocalDateTime created) {
    this.created = created;
  }
  
  public Integer getConfidence() {
    return confidence;
  }
  
  public void setConfidence(Integer confidence) {
    this.confidence = confidence;
  }
  
  public Integer getCompleteness() {
    return completeness;
  }
  
  public void setCompleteness(Integer completeness) {
    this.completeness = completeness;
  }
  
  public String getNotes() {
    return notes;
  }
  
  public void setNotes(String notes) {
    this.notes = notes;
  }
  
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ColSource colSource = (ColSource) o;
    return Objects.equals(key, colSource.key) &&
        Objects.equals(datasetKey, colSource.datasetKey) &&
        Objects.equals(title, colSource.title) &&
        Objects.equals(alias, colSource.alias) &&
        Objects.equals(description, colSource.description) &&
        Objects.equals(organisations, colSource.organisations) &&
        Objects.equals(contactPerson, colSource.contactPerson) &&
        Objects.equals(authorsAndEditors, colSource.authorsAndEditors) &&
        Objects.equals(version, colSource.version) &&
        Objects.equals(released, colSource.released) &&
        Objects.equals(website, colSource.website) &&
        Objects.equals(group, colSource.group) &&
        coverage == colSource.coverage &&
        Objects.equals(citation, colSource.citation) &&
        Objects.equals(livingSpeciesCount, colSource.livingSpeciesCount) &&
        Objects.equals(livingInfraspecificCount, colSource.livingInfraspecificCount) &&
        Objects.equals(extinctSpeciesCount, colSource.extinctSpeciesCount) &&
        Objects.equals(extinctInfraspecificCount, colSource.extinctInfraspecificCount) &&
        Objects.equals(synonymsCount, colSource.synonymsCount) &&
        Objects.equals(vernacularsCount, colSource.vernacularsCount) &&
        Objects.equals(namesCount, colSource.namesCount) &&
        Objects.equals(created, colSource.created) &&
        Objects.equals(confidence, colSource.confidence) &&
        Objects.equals(completeness, colSource.completeness) &&
        Objects.equals(notes, colSource.notes);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, datasetKey, title, alias, description, organisations, contactPerson, authorsAndEditors, version, released, website, group, coverage, citation, livingSpeciesCount, livingInfraspecificCount, extinctSpeciesCount, extinctInfraspecificCount, synonymsCount, vernacularsCount, namesCount, created, confidence, completeness, notes);
  }
  
  @Override
  public String toString() {
    return "ColSource " + key + ": " + alias;
  }
}
