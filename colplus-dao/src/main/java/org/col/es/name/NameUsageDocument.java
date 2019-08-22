package org.col.es.name;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NameField;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.ddl.Analyzers;
import org.col.es.ddl.ESDataType;
import org.col.es.ddl.MapToType;
import org.col.es.ddl.NotIndexed;
import org.col.es.ddl.NotMapped;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import static org.col.es.ddl.Analyzer.AUTO_COMPLETE;
import static org.col.es.ddl.Analyzer.IGNORE_CASE;

/**
 * Class modeling the Elasticsearch document type used to store NameUsageWrapper instances.
 */
public class NameUsageDocument {

  // Elasticsearch's own id for the document. Note that this id is NOT part of the document and document type mapping. It comes
  // along as metadata with the search response, outside the JSON document itself. We "artificially" add it after deserialization of the
  // JSON document. Therefore the getter for documentId is @NotMapped.
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
  private NomCode nomCode;
  private NomStatus nomStatus;
  private Set<NameField> nameFields;
  private TaxonomicStatus status;
  private Set<Issue> issues;
  private List<String> vernacularNames;
  private List<String> classificationIds;
  private List<Monomial> classification;
  private Boolean fossil;
  private Boolean recent;
  private String payload;

  @NotMapped
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

  // We don't actually ever search on the scientific name as-is, only on the strings in the NameStrings objects. However, we still place it
  // outside the payload though so we don't even need to unpack the payload for the auto-completion service.
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

  public NomCode getNomCode() {
    return nomCode;
  }

  public void setNomCode(NomCode nomCode) {
    this.nomCode = nomCode;
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

  @Override
  public int hashCode() {
    return Objects.hash(authorship, classification, classificationIds, datasetKey, decisionKey, documentId, fossil, issues, nameFields,
        nameId, nameIndexId, nameStrings, nomCode, nomStatus, payload, publishedInId, publisherKey, rank, recent, scientificName, sectorKey,
        status, type, usageId, vernacularNames);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NameUsageDocument other = (NameUsageDocument) obj;
    return Objects.equals(authorship, other.authorship) && Objects.equals(classification, other.classification)
        && Objects.equals(classificationIds, other.classificationIds) && Objects.equals(datasetKey, other.datasetKey)
        && Objects.equals(decisionKey, other.decisionKey) && Objects.equals(documentId, other.documentId)
        && Objects.equals(fossil, other.fossil) && Objects.equals(issues, other.issues) && Objects.equals(nameFields, other.nameFields)
        && Objects.equals(nameId, other.nameId) && Objects.equals(nameIndexId, other.nameIndexId)
        && Objects.equals(nameStrings, other.nameStrings) && nomCode == other.nomCode && nomStatus == other.nomStatus
        && Objects.equals(payload, other.payload) && Objects.equals(publishedInId, other.publishedInId)
        && Objects.equals(publisherKey, other.publisherKey) && rank == other.rank && Objects.equals(recent, other.recent)
        && Objects.equals(scientificName, other.scientificName) && Objects.equals(sectorKey, other.sectorKey) && status == other.status
        && type == other.type && Objects.equals(usageId, other.usageId) && Objects.equals(vernacularNames, other.vernacularNames);
  }


}
