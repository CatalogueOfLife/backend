package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.col.api.constraints.AbsoluteURI;
import org.col.api.constraints.NotBlank;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.NomCode;

/**
 * Metadata about a dataset or a subset of it if parentKey is given.
 */
public class Dataset extends DataEntity implements IntKey {
  private Integer key;
  @NotNull
  private DatasetType type = DatasetType.OTHER;
  @NotNull
  @NotBlank
  private String title;
  private String alias;
  private UUID gbifKey;
  private UUID gbifPublisherKey;
  private String description;
  private List<String> organisations = Lists.newArrayList();
  private String contact;
  private List<String> authorsAndEditors = Lists.newArrayList();
  private License license;
  private String version;
  private LocalDate released;
  private String citation;
  @AbsoluteURI
  private URI website;
  private String group;
  @AbsoluteURI
  private URI logo;
  private DataFormat dataFormat;
  @AbsoluteURI
  private URI dataAccess;
  @NotNull
  private DatasetOrigin origin = DatasetOrigin.UPLOADED;
  private Frequency importFrequency;
  private NomCode code;
  private Integer size;
  @Max(5)
  @Min(1)
  private Integer confidence;
  @Max(100)
  @Min(0)
  private Integer completeness;
  private String notes;
  private Catalogue contributesTo;
  private LocalDateTime imported;
  private LocalDateTime deleted;
  
  public Integer getKey() {
    return key;
  }
  
  public DatasetType getType() {
    return type;
  }
  
  public void setType(DatasetType type) {
    this.type = Preconditions.checkNotNull(type);
  }
  
  public void setKey(Integer key) {
    this.key = key;
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
  
  public UUID getGbifPublisherKey() {
    return gbifPublisherKey;
  }
  
  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    this.gbifPublisherKey = gbifPublisherKey;
  }
  
  public String getDescription() {
    return description;
  }
  
  public void setDescription(String description) {
    this.description = description;
  }
  
  /**
   * The nomenclatural code followed in this dataset.
   * It will be used mostly as a hint to format names accordingly.
   * If the dataset contains mixed data from multiple codes keep this field null.
   *
   * @return the nomenclatural code applying to all data in this dataset or null
   */
  public NomCode getCode() {
    return code;
  }
  
  public void setCode(NomCode code) {
    this.code = code;
  }
  
  public List<String> getAuthorsAndEditors() {
    return authorsAndEditors;
  }
  
  public void setAuthorsAndEditors(List<String> authorsAndEditors) {
    this.authorsAndEditors = authorsAndEditors;
  }
  
  public List<String> getOrganisations() {
    return organisations;
  }
  
  public void setOrganisations(List<String> organisations) {
    this.organisations = organisations;
  }
  
  public String getContact() {
    return contact;
  }
  
  public void setContact(String contact) {
    this.contact = contact;
  }
  
  public License getLicense() {
    return license;
  }
  
  public void setLicense(License license) {
    this.license = license;
  }
  
  public String getVersion() {
    return version;
  }
  
  public void setVersion(String version) {
    this.version = version;
  }
  
  /**
   * Release date of the source data.
   * The date can usually only be taken from metadata explicitly given by the source.
   */
  public LocalDate getReleased() {
    return released;
  }
  
  public void setReleased(LocalDate released) {
    this.released = released;
  }
  
  public String getCitation() {
    return citation;
  }
  
  public void setCitation(String citation) {
    this.citation = citation;
  }
  
  public URI getWebsite() {
    return website;
  }
  
  public void setWebsite(URI website) {
    this.website = website;
  }
  
  public URI getLogo() {
    return logo;
  }
  
  public void setLogo(URI logo) {
    this.logo = logo;
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
  
  public DatasetOrigin getOrigin() {
    return origin;
  }
  
  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }
  
  public Frequency getImportFrequency() {
    return importFrequency;
  }
  
  public void setImportFrequency(Frequency importFrequency) {
    this.importFrequency = importFrequency;
  }
  
  public String getNotes() {
    return notes;
  }
  
  public void setNotes(String notes) {
    this.notes = notes;
  }
  
  public Integer getSize() {
    return size;
  }
  
  public void setSize(Integer size) {
    this.size = size;
  }
  
  /**
   * If the dataset participates in any of the 2 catalogue assemblies
   * this is indicated here. All scrutinized sources will also be included as provisional ones.
   * <p>
   * Dataset used to build the provisional catalogue will be trusted and insert their names into the names index.
   */
  public Catalogue getContributesTo() {
    return contributesTo;
  }
  
  public void setContributesTo(Catalogue contributesTo) {
    this.contributesTo = contributesTo;
  }

  /**
   * Time the data of the dataset was last changed in the Clearinghouse,
   * i.e. time of the last import that changed at least one record.
   */
  public LocalDateTime getImported() {
    return imported;
  }

  public void setImported(LocalDateTime imported) {
    this.imported = imported;
  }


  public LocalDateTime getDeleted() {
    return deleted;
  }
  
  @JsonIgnore
  public boolean hasDeletedDate() {
    return deleted != null;
  }
  
  public void setDeleted(LocalDateTime deleted) {
    this.deleted = deleted;
  }
  
  public String getAlias() {
    return alias;
  }
  
  public void setAlias(String alias) {
    this.alias = alias;
  }
  
  public String getGroup() {
    return group;
  }
  
  public void setGroup(String group) {
    this.group = group;
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
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Dataset dataset = (Dataset) o;
    return Objects.equals(key, dataset.key) &&
        type == dataset.type &&
        Objects.equals(title, dataset.title) &&
        Objects.equals(alias, dataset.alias) &&
        Objects.equals(gbifKey, dataset.gbifKey) &&
        Objects.equals(gbifPublisherKey, dataset.gbifPublisherKey) &&
        Objects.equals(description, dataset.description) &&
        Objects.equals(organisations, dataset.organisations) &&
        Objects.equals(contact, dataset.contact) &&
        Objects.equals(authorsAndEditors, dataset.authorsAndEditors) &&
        license == dataset.license &&
        Objects.equals(version, dataset.version) &&
        Objects.equals(released, dataset.released) &&
        Objects.equals(citation, dataset.citation) &&
        Objects.equals(website, dataset.website) &&
        Objects.equals(group, dataset.group) &&
        Objects.equals(logo, dataset.logo) &&
        dataFormat == dataset.dataFormat &&
        Objects.equals(dataAccess, dataset.dataAccess) &&
        origin == dataset.origin &&
        importFrequency == dataset.importFrequency &&
        code == dataset.code &&
        Objects.equals(size, dataset.size) &&
        Objects.equals(confidence, dataset.confidence) &&
        Objects.equals(completeness, dataset.completeness) &&
        Objects.equals(notes, dataset.notes) &&
        contributesTo == dataset.contributesTo &&
        Objects.equals(imported, dataset.imported) &&
        Objects.equals(deleted, dataset.deleted);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, type, title, alias, gbifKey, gbifPublisherKey, description, organisations, contact, authorsAndEditors, license, version, released, citation, website, group, logo, dataFormat, dataAccess, origin, importFrequency, code, size, confidence, completeness, notes, contributesTo, imported, deleted);
  }
  
  @Override
  public String toString() {
    return "Dataset " + key + ": " + title;
  }
}
