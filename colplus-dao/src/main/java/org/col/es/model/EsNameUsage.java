package org.col.es.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NameField;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.annotations.Analyzers;
import org.col.es.annotations.NotIndexed;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import static org.col.es.annotations.Analyzer.AUTO_COMPLETE;

public class EsNameUsage {

  private String scientificName;
  private String authorship;

  private int datasetKey;
  private String nameId;
  private String nameIndexId;
  private String publishedInId;
  private Rank rank;
  private NameType type;
  private NomStatus nomStatus;
  private Set<NameField> nameFields;
  private TaxonomicStatus status;
  private String taxonId;

  private Set<Issue> issues;
  private List<String> vernacularNames;

  private String payload; // The entire NameUsageWrapper object, serialized to a string

  @Analyzers({AUTO_COMPLETE})
  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public String getNameId() {
    return nameId;
  }

  public void setNameId(String nameId) {
    this.nameId = nameId;
  }

  public String getNameIndexId() {
    return nameIndexId;
  }

  public void setNameIndexId(String nameIndexId) {
    this.nameIndexId = nameIndexId;
  }

  public String getPublishedInId() {
    return publishedInId;
  }

  public void setPublishedInId(String publishedInId) {
    this.publishedInId = publishedInId;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public NameType getType() {
    return type;
  }

  public void setType(NameType type) {
    this.type = type;
  }

  public NomStatus getNomStatus() {
    return nomStatus;
  }

  public void setNomStatus(NomStatus nomStatus) {
    this.nomStatus = nomStatus;
  }

  public Set<NameField> getNameFields() {
    return nameFields;
  }

  public void setNameFields(Set<NameField> nameFields) {
    this.nameFields = nameFields;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public String getTaxonId() {
    return taxonId;
  }

  public void setTaxonId(String taxonId) {
    this.taxonId = taxonId;
  }

  @Analyzers({AUTO_COMPLETE})
  public List<String> getVernacularNames() {
    return vernacularNames;
  }

  public void setVernacularNames(List<String> vernacularNames) {
    this.vernacularNames = vernacularNames;
  }

  public Set<Issue> getIssues() {
    return issues;
  }

  public void setIssues(Set<Issue> issues) {
    this.issues = issues;
  }

  @NotIndexed
  public String getPayload() {
    return payload;
  }

  public void setPayload(String source) {
    this.payload = source;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    EsNameUsage that = (EsNameUsage) o;
    return datasetKey == that.datasetKey && Objects.equals(scientificName, that.scientificName)
        && Objects.equals(authorship, that.authorship)
        && Objects.equals(vernacularNames, that.vernacularNames)
        && Objects.equals(nameId, that.nameId) && Objects.equals(nameIndexId, that.nameIndexId)
        && Objects.equals(publishedInId, that.publishedInId) && rank == that.rank
        && type == that.type && nomStatus == that.nomStatus
        && Objects.equals(nameFields, that.nameFields) && status == that.status
        && Objects.equals(taxonId, that.taxonId) && Objects.equals(issues, that.issues)
        && Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {

    return Objects.hash(scientificName, authorship, vernacularNames, datasetKey, nameId,
        nameIndexId, publishedInId, rank, type, nomStatus, nameFields, status, taxonId, issues,
        payload);
  }

}
