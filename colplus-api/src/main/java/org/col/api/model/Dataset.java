package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
public class Dataset extends DataEntity implements SourceMetadata, IntKey {
  private Integer key;
  @NotNull
  private DatasetType type = DatasetType.OTHER;
  @NotNull
  @NotBlank
  private String title;
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
  @AbsoluteURI
  private URI logo;
  private DataFormat dataFormat;
  @AbsoluteURI
  private URI dataAccess;
  private boolean cluster = false;
  @NotNull
  private DatasetOrigin origin = DatasetOrigin.UPLOADED;
  private Frequency importFrequency;
  private NomCode code;
  private Integer size;
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
  
  @Override
  public String getTitle() {
    return title;
  }
  
  @Override
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
  
  @Override
  public String getDescription() {
    return description;
  }
  
  @Override
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
  
  @Override
  public List<String> getAuthorsAndEditors() {
    return authorsAndEditors;
  }
  
  @Override
  public void setAuthorsAndEditors(List<String> authorsAndEditors) {
    this.authorsAndEditors = authorsAndEditors;
  }
  
  public List<String> getOrganisations() {
    return organisations;
  }
  
  public void setOrganisations(List<String> organisations) {
    this.organisations = organisations;
  }
  
  @Override
  public String getContact() {
    return contact;
  }
  
  @Override
  public void setContact(String contact) {
    this.contact = contact;
  }
  
  public License getLicense() {
    return license;
  }
  
  public void setLicense(License license) {
    this.license = license;
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
  public String getCitation() {
    return citation;
  }
  
  @Override
  public void setCitation(String citation) {
    this.citation = citation;
  }
  
  @Override
  public URI getWebsite() {
    return website;
  }
  
  @Override
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
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/163
   *
   * @return true if multiple col sources are allowed
   */
  public boolean isCluster() {
    return cluster;
  }
  
  public void setCluster(boolean cluster) {
    this.cluster = cluster;
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
  
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Dataset dataset = (Dataset) o;
    return cluster == dataset.cluster &&
        Objects.equals(key, dataset.key) &&
        type == dataset.type &&
        Objects.equals(title, dataset.title) &&
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
        Objects.equals(logo, dataset.logo) &&
        dataFormat == dataset.dataFormat &&
        Objects.equals(dataAccess, dataset.dataAccess) &&
        origin == dataset.origin &&
        importFrequency == dataset.importFrequency &&
        code == dataset.code &&
        Objects.equals(size, dataset.size) &&
        Objects.equals(notes, dataset.notes) &&
        contributesTo == dataset.contributesTo &&
        Objects.equals(imported, dataset.imported) &&
        Objects.equals(deleted, dataset.deleted);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, type, title, gbifKey, gbifPublisherKey, description, organisations, contact, authorsAndEditors, license, version, released, citation, website, logo, dataFormat, dataAccess, cluster, origin, importFrequency, code, size, notes, contributesTo, imported, deleted);
  }
  
  @Override
  public String toString() {
    return "Dataset " + key + ": " + title;
  }
}
