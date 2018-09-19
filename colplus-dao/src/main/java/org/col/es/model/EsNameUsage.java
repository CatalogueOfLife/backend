package org.col.es.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Taxon;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;

public class EsNameUsage implements NameUsage {

  private String id;

  private Integer datasetKey;

  private Integer verbatimKey;

  private Name name;

  private Origin origin;

  private String parentId;

  private String accordingTo;

  private LocalDate accordingToDate;

  private Boolean fossil;

  private Boolean recent;

  private Set<Lifezone> lifezones = EnumSet.noneOf(Lifezone.class);

  private URI datasetUrl;

  private Integer speciesEstimate;

  private String speciesEstimateReferenceId;

  private String remarks;

  private TaxonomicStatus status;

  private Taxon accepted;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Integer getVerbatimKey() {
    return verbatimKey;
  }

  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  public Name getName() {
    return name;
  }

  public void setName(Name name) {
    this.name = name;
  }

  public Origin getOrigin() {
    return origin;
  }

  public void setOrigin(Origin origin) {
    this.origin = origin;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
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

  public String getSpeciesEstimateReferenceId() {
    return speciesEstimateReferenceId;
  }

  public void setSpeciesEstimateReferenceId(String speciesEstimateReferenceId) {
    this.speciesEstimateReferenceId = speciesEstimateReferenceId;
  }

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public Taxon getAccepted() {
    return accepted;
  }

  public void setAccepted(Taxon accepted) {
    this.accepted = accepted;
  }

}
