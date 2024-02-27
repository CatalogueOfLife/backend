package life.catalogue.es;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.*;
import life.catalogue.es.ddl.*;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.*;

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
  @MapToType(ESDataType.KEYWORD)
  private UUID sectorPublisherKey;
  private Sector.Mode sectorMode;
  private Set<InfoGroup> secondarySourceGroup;
  @Analyzers(KEYWORD)
  private Set<Integer> secondarySourceKey;

  @Analyzers({KEYWORD, SCINAME_IGNORE_CASE, SCINAME_WHOLE_WORDS, SCINAME_AUTO_COMPLETE})
  private String scientificName;
  private NameStrings nameStrings;
  // Only indexed using KEYWORD analyzer since it's meant for faceting only (#371)
  private Set<String> authorship;
  private Set<String> authorshipYear;
  @Analyzers(STANDARD_ASCII)
  private String authorshipComplete;
  private String nameId;
  private String publishedInId;
  @MapToType(ESDataType.KEYWORD)
  private UUID publisherKey;
  private Rank rank;
  private Origin origin;
  private NameType type;
  private NomCode nomCode;
  private NomStatus nomStatus;
  private Set<NameField> nameFields;
  private TaxonomicStatus status;
  private TaxGroup group;
  private Set<Issue> issues;
  private Set<Environment> environments;
  private Boolean extinct;

  // The first entry in the classification list represents the usage itself
  @Analyzers(KEYWORD)
  private List<String> classificationIds;
  // The first entry in the classification list represents the usage itself
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

  public Origin getOrigin() {
    return origin;
  }

  public void setOrigin(Origin origin) {
    this.origin = origin;
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

  public Set<Environment> getEnvironments() {
    return environments;
  }

  public void setEnvironments(Set<Environment> environments) {
    this.environments = environments;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EsNameUsage that = (EsNameUsage) o;
    return Objects.equals(documentId, that.documentId) && Objects.equals(usageId, that.usageId) && Objects.equals(datasetKey, that.datasetKey) && Objects.equals(sectorKey, that.sectorKey) && Objects.equals(sectorDatasetKey, that.sectorDatasetKey) && Objects.equals(sectorPublisherKey, that.sectorPublisherKey) && sectorMode == that.sectorMode && Objects.equals(secondarySourceGroup, that.secondarySourceGroup) && Objects.equals(secondarySourceKey, that.secondarySourceKey) && Objects.equals(scientificName, that.scientificName) && Objects.equals(nameStrings, that.nameStrings) && Objects.equals(authorship, that.authorship) && Objects.equals(authorshipYear, that.authorshipYear) && Objects.equals(authorshipComplete, that.authorshipComplete) && Objects.equals(nameId, that.nameId) && Objects.equals(publishedInId, that.publishedInId) && Objects.equals(publisherKey, that.publisherKey) && rank == that.rank && origin == that.origin && type == that.type && nomCode == that.nomCode && nomStatus == that.nomStatus && Objects.equals(nameFields, that.nameFields) && status == that.status && group == that.group && Objects.equals(issues, that.issues) && Objects.equals(environments, that.environments) && Objects.equals(extinct, that.extinct) && Objects.equals(classificationIds, that.classificationIds) && Objects.equals(classification, that.classification) && Objects.equals(decisions, that.decisions) && Objects.equals(acceptedName, that.acceptedName) && Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(documentId, usageId, datasetKey, sectorKey, sectorDatasetKey, sectorPublisherKey, sectorMode, secondarySourceGroup, secondarySourceKey, scientificName, nameStrings, authorship, authorshipYear, authorshipComplete, nameId, publishedInId, publisherKey, rank, origin, type, nomCode, nomStatus, nameFields, status, group, issues, environments, extinct, classificationIds, classification, decisions, acceptedName, payload);
  }

  public TaxGroup getGroup() {
    return group;
  }

  public void setGroup(TaxGroup group) {
    this.group = group;
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

  public UUID getSectorPublisherKey() {
    return sectorPublisherKey;
  }

  public void setSectorPublisherKey(UUID sectorPublisherKey) {
    this.sectorPublisherKey = sectorPublisherKey;
  }

  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  public Set<InfoGroup> getSecondarySourceGroup() {
    return secondarySourceGroup;
  }

  public void setSecondarySourceGroup(Set<InfoGroup> secondarySourceGroup) {
    this.secondarySourceGroup = secondarySourceGroup;
  }

  public Set<Integer> getSecondarySourceKey() {
    return secondarySourceKey;
  }

  public void setSecondarySourceKey(Set<Integer> secondarySourceKey) {
    this.secondarySourceKey = secondarySourceKey;
  }

}
