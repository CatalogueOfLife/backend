package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonIssue;
import org.col.api.vocab.TaxonomicStatus;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class Taxon {

  /**
   * Internal surrogate key of the taxon as provided by postgres.
   * This key is unique across all datasets but not exposed in the API.
   */
  @JsonIgnore
  private Integer keyInternal;

  /**
   * Primary key of the taxon as given in the dataset as taxonID.
   * Only guaranteed to be unique within a dataset and can follow any kind of schema.
   */
  private String key;

  /**
   * Key to dataset instance. Defines context of the taxon key.
   */
  private Dataset dataset;

  /**
   *
   */
  private Name name;

  private TaxonomicStatus status;

  private Rank rank;

  private Taxon parent;

  private String accordingTo;

  private LocalDate accordingToDate;

  private Boolean fossil;

  private Boolean recent;

  private Set<Lifezone> lifezones;

  private URI datasetUrl;

  private Integer speciesEstimate;

  private Reference speciesEstimateReference;

  /**
   * Issues related to this taxon with potential values in the map
   */
  private Map<TaxonIssue, Object> issues = new EnumMap(TaxonIssue.class);

  public Integer getKeyInternal() {
    return keyInternal;
  }

  public void setKeyInternal(Integer keyInternal) {
    this.keyInternal = keyInternal;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public void setDataset(Dataset dataset) {
    this.dataset = dataset;
  }

  public Name getName() {
    return name;
  }

  public void setName(Name name) {
    this.name = name;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public Taxon getParent() {
    return parent;
  }

  public void setParent(Taxon parent) {
    this.parent = parent;
  }

  public String getAccordingTo() {
    return accordingTo;
  }

  public void setAccordingTo(String accordingTo) {
    this.accordingTo = accordingTo;
  }

  public LocalDate getAccordingToDate() {
    return accordingToDate;
  }

  public void setAccordingToDate(LocalDate accordingToDate) {
    this.accordingToDate = accordingToDate;
  }

  public Boolean getFossil() {
    return fossil;
  }

  public void setFossil(Boolean fossil) {
    this.fossil = fossil;
  }

  public Boolean getRecent() {
    return recent;
  }

  public void setRecent(Boolean recent) {
    this.recent = recent;
  }

  public Set<Lifezone> getLifezones() {
    return lifezones;
  }

  public void setLifezones(Set<Lifezone> lifezones) {
    this.lifezones = lifezones;
  }

  public URI getDatasetUrl() {
    return datasetUrl;
  }

  public void setDatasetUrl(URI datasetUrl) {
    this.datasetUrl = datasetUrl;
  }

  public Integer getSpeciesEstimate() {
    return speciesEstimate;
  }

  public void setSpeciesEstimate(Integer speciesEstimate) {
    this.speciesEstimate = speciesEstimate;
  }

  public Reference getSpeciesEstimateReference() {
    return speciesEstimateReference;
  }

  public void setSpeciesEstimateReference(Reference speciesEstimateReference) {
    this.speciesEstimateReference = speciesEstimateReference;
  }

  public Map<TaxonIssue, Object> getIssues() {
    return issues;
  }

  public void setIssues(Map<TaxonIssue, Object> issues) {
    this.issues = issues;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Taxon taxon = (Taxon) o;
    return Objects.equals(keyInternal, taxon.keyInternal) &&
        Objects.equals(key, taxon.key) &&
        Objects.equals(dataset, taxon.dataset) &&
        Objects.equals(name, taxon.name) &&
        status == taxon.status &&
        rank == taxon.rank &&
        Objects.equals(parent, taxon.parent) &&
        Objects.equals(accordingTo, taxon.accordingTo) &&
        Objects.equals(accordingToDate, taxon.accordingToDate) &&
        Objects.equals(fossil, taxon.fossil) &&
        Objects.equals(recent, taxon.recent) &&
        Objects.equals(lifezones, taxon.lifezones) &&
        Objects.equals(datasetUrl, taxon.datasetUrl) &&
        Objects.equals(speciesEstimate, taxon.speciesEstimate) &&
        Objects.equals(speciesEstimateReference, taxon.speciesEstimateReference) &&
        Objects.equals(issues, taxon.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyInternal, key, dataset, name, status, rank, parent, accordingTo, accordingToDate, fossil, recent, lifezones, datasetUrl, speciesEstimate, speciesEstimateReference, issues);
  }
}
