package life.catalogue.es;

import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NameField;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.ddl.*;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static life.catalogue.es.ddl.Analyzer.*;

/**
 * Class modeling the Elasticsearch document type used to store NameUsageWrapper instances.
 */
public class EsNameUsage {

  @NotMapped
  private String documentId;

  private String usageId;
  @MapToType(ESDataType.KEYWORD)
  private Integer datasetKey;
  @MapToType(ESDataType.KEYWORD)
  private Integer sectorKey;
  @MapToType(ESDataType.KEYWORD)
  private Integer sectorDatasetKey;
  @Analyzers({KEYWORD, SCINAME_IGNORE_CASE, SCINAME_WHOLE_WORDS, SCINAME_AUTO_COMPLETE})
  private String scientificName;
  private NameStrings nameStrings;
  // Only indexed using KEYWORD analyzer since it's meant for faceting only (#371)
  private Set<String> authorship;
  private Set<String> authorshipYear;
  @Analyzers({IGNORE_CASE, AUTO_COMPLETE})
  private String authorshipComplete;
  private String nameId;
  private Set<Integer> nameIndexIds;
  private String publishedInId;
  @MapToType(ESDataType.KEYWORD)
  private UUID publisherKey;
  private Rank rank;
  private NameType type;
  private NomCode nomCode;
  private NomStatus nomStatus;
  private Set<NameField> nameFields;
  private TaxonomicStatus status;
  private Set<Issue> issues;
  @Analyzers({IGNORE_CASE, AUTO_COMPLETE})
  private List<String> vernacularNames;
  private List<String> classificationIds;
  private Boolean extinct;

  @MapToType(ESDataType.OBJECT)
  private List<EsMonomial> classification;
  
  // Nested documents. Will require special query logic!
  private List<EsDecision> decisions;

  @NotIndexed
  private String acceptedName;

  @MapToType(ESDataType.BINARY)
  private String payload;

  /**
   * Elasticsearch's own id for the document. Note that this id is NOT part of the document and must therefore not be included in the
   * document type mapping. It comes along as metadata with the search response, outside the JSON document itself. We artificially add it
   * after deserialization of the JSON document. When indexing this field **must** be null. Since we use strict typing, Elasticsearch would
   * complain if the field were present in the JSON document.
   */
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

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  public Integer getSectorDatasetKey() {
    return sectorDatasetKey;
  }

  public void setSectorDatasetKey(Integer sectorDatasetKey) {
    this.sectorDatasetKey = sectorDatasetKey;
  }

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

  public Set<String> getAuthorship() {
    return authorship;
  }

  public Set<String> getAuthorshipYear() {
    return authorshipYear;
  }

  public void setAuthorship(Set<String> authorship) {
    this.authorship = authorship;
  }

  public void setAuthorshipYear(Set<String> authorshipYear) {
    this.authorshipYear = authorshipYear;
  }

  public String getAuthorshipComplete() {
    return authorshipComplete;
  }

  public void setAuthorshipComplete(String authorship) {
    this.authorshipComplete = authorship;
  }

  public String getNameId() {
    return nameId;
  }

  public void setNameId(String nameId) {
    this.nameId = nameId;
  }

  public Set<Integer> getNameIndexIds() {
    return nameIndexIds;
  }

  public void setNameIndexIds(Set<Integer> nameIndexIds) {
    this.nameIndexIds = nameIndexIds;
  }

  public String getPublishedInId() {
    return publishedInId;
  }

  public void setPublishedInId(String publishedInId) {
    this.publishedInId = publishedInId;
  }

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

  public List<String> getClassificationIds() {
    return classificationIds;
  }

  public void setClassificationIds(List<String> higherTaxonIds) {
    this.classificationIds = higherTaxonIds;
  }

  public List<EsMonomial> getClassification() {
    return classification;
  }

  public void setClassification(List<EsMonomial> monomials) {
    this.classification = monomials;
  }

  public Boolean getExtinct() {
    return extinct;
  }

  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
  }

  /**
   * If this document represents a synonym this field contains the accepted name, otherwise it is null. Not indexed (searchable), but still
   * placed outside the payload, so we can quickly access it in the name suggestion service.
   */
  public String getAcceptedName() {
    return acceptedName;
  }

  public void setAcceptedName(String acceptedName) {
    this.acceptedName = acceptedName;
  }

  /**
   * Contains the (possibly zipped) serialization of the entire NameUsageWrapper object as we got it from postgres. This is stored as a
   * (base64-encoded) binary field, which never is indexed (no need to annotate it as such).
   */
  public String getPayload() {
    return payload;
  }

  public void setPayload(String source) {
    this.payload = source;
  }

  public List<EsDecision> getDecisions() {
    return decisions;
  }

  public void setDecisions(List<EsDecision> decisions) {
    this.decisions = decisions;
  }

  @Override
  public int hashCode() {
    return Objects.hash(acceptedName, authorship, authorshipComplete, authorshipYear, classification, classificationIds, datasetKey,
        decisions, documentId, extinct, issues, nameFields, nameId, nameIndexIds, nameStrings, nomCode, nomStatus, payload,
        publishedInId, publisherKey, rank, scientificName, sectorDatasetKey, sectorKey, status, type, usageId, vernacularNames);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    EsNameUsage other = (EsNameUsage) obj;
    return Objects.equals(acceptedName, other.acceptedName) && Objects.equals(authorship, other.authorship)
        && Objects.equals(authorshipComplete, other.authorshipComplete) && Objects.equals(authorshipYear, other.authorshipYear)
        && Objects.equals(classification, other.classification) && Objects.equals(classificationIds, other.classificationIds)
        && Objects.equals(datasetKey, other.datasetKey)
        && Objects.equals(decisions, other.decisions) && Objects.equals(documentId, other.documentId)
        && Objects.equals(extinct, other.extinct) && Objects.equals(issues, other.issues) && Objects.equals(nameFields, other.nameFields)
        && Objects.equals(nameId, other.nameId) && Objects.equals(nameIndexIds, other.nameIndexIds)
        && Objects.equals(nameStrings, other.nameStrings) && nomCode == other.nomCode && nomStatus == other.nomStatus
        && Objects.equals(payload, other.payload) && Objects.equals(publishedInId, other.publishedInId)
        && Objects.equals(publisherKey, other.publisherKey) && rank == other.rank
        && Objects.equals(scientificName, other.scientificName) && Objects.equals(sectorDatasetKey, other.sectorDatasetKey)
        && Objects.equals(sectorKey, other.sectorKey) && status == other.status && type == other.type
        && Objects.equals(usageId, other.usageId) && Objects.equals(vernacularNames, other.vernacularNames);
  }

}
