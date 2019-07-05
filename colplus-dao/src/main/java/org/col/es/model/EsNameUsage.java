package org.col.es.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NameField;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.annotations.Analyzers;
import org.col.es.annotations.MapToType;
import org.col.es.annotations.NotIndexed;
import org.col.es.mapping.ESDataType;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import static org.col.es.annotations.Analyzer.AUTO_COMPLETE;
import static org.col.es.annotations.Analyzer.IGNORE_CASE;

/**
 * Class modeling the Elasticsearch document type used to store NameUsageWrapper instances.
 */
public class EsNameUsage {

  // Elasticsearch's own id for the document
  private String documentId;

  private String usageId;
  private Integer datasetKey;
  private Integer sectorKey;

  private String scientificName;
  private NameStrings nameStrings;

  private String authorship;
  private String nameId;
  private String nameIndexId;
  private String publishedInId;
  private Integer decisionKey;
  private UUID publisherKey;
  private Rank rank;
  private NameType type;
  private NomStatus nomStatus;
  private Set<NameField> nameFields;
  private TaxonomicStatus status;
  private Set<Issue> issues;
  private List<String> vernacularNames;
  private List<String> classificationIds;
  private List<Monomial> classification;
  private String payload;

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public String getUsageId() {
    return usageId;
  }

  public void setUsageId(String usageId) {
    this.usageId = usageId;
  }

  @MapToType(ESDataType.KEYWORD)
  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  @MapToType(ESDataType.KEYWORD)
  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  /*
   * We don't actually search on the scientific name itself, only on the strings in the SearchableNameStrings. We place it outside the
   * payload though so we don't even need to unpack the payload for the auto-completion service.
   */
  @NotIndexed
  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  public NameStrings getNameStrings() {
    return nameStrings;
  }

  public void setNameStrings(NameStrings nameStrings) {
    this.nameStrings = nameStrings;
  }

  @Analyzers({AUTO_COMPLETE, IGNORE_CASE})
  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
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

  @MapToType(ESDataType.KEYWORD)
  public Integer getDecisionKey() {
    return decisionKey;
  }

  public void setDecisionKey(Integer decisionKey) {
    this.decisionKey = decisionKey;
  }

  @MapToType(ESDataType.KEYWORD)
  public UUID getPublisherKey() {
    return publisherKey;
  }

  public void setPublisherKey(UUID publisherKey) {
    this.publisherKey = publisherKey;
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

  @Analyzers({AUTO_COMPLETE, IGNORE_CASE})
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

  @MapToType(ESDataType.BINARY)
  public String getPayload() {
    return payload;
  }

  public void setPayload(String source) {
    this.payload = source;
  }

  public List<String> getClassificationIds() {
    return classificationIds;
  }

  public void setClassificationIds(List<String> higherTaxonIds) {
    this.classificationIds = higherTaxonIds;
  }

  public List<Monomial> getClassification() {
    return classification;
  }

  public void setClassification(List<Monomial> monomials) {
    this.classification = monomials;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    EsNameUsage that = (EsNameUsage) o;
    return true
        && Objects.equals(documentId, that.documentId)
        && Objects.equals(authorship, that.authorship)
        && Objects.equals(classification, that.classification)
        && Objects.equals(classificationIds, that.classificationIds)
        && Objects.equals(datasetKey, that.datasetKey)
        && Objects.equals(decisionKey, that.decisionKey)
        && Objects.equals(nameIndexId, that.nameIndexId)
        && Objects.equals(issues, that.issues)
        && Objects.equals(nameFields, that.nameFields)
        && Objects.equals(nameId, that.nameId)
        && nomStatus == that.nomStatus
        && Objects.equals(payload, that.payload)
        && Objects.equals(publishedInId, that.publishedInId)
        && Objects.equals(publisherKey, that.publisherKey)
        && rank == that.rank
        && Objects.equals(nameStrings, that.nameStrings)
        && status == that.status
        && type == that.type
        && Objects.equals(usageId, that.usageId)
        && Objects.equals(vernacularNames, that.vernacularNames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        documentId,
        authorship,
        classification,
        classificationIds,
        datasetKey,
        decisionKey,
        nameIndexId,
        issues,
        nameFields,
        nameId,
        nomStatus,
        payload,
        publishedInId,
        publisherKey,
        rank,
        nameStrings,
        status,
        type,
        usageId,
        vernacularNames);
  }

}
