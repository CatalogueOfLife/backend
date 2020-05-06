package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.constraints.AbsoluteURI;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Metadata about a dataset or a subset of it if parentKey is given.
 */
public class Dataset extends DataEntity<Integer> implements DatasetMetadata {
  private static final PropertyDescriptor[] METADATA_PROPS;
  static {
    try {
      METADATA_PROPS = Introspector.getBeanInfo(DatasetMetadata.class).getPropertyDescriptors();
    } catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }
  }
  // internal keys & flags
  private Integer key;
  private Integer sourceKey;
  private Integer importAttempt;
  @NotNull
  private DatasetType type;
  @NotNull
  private DatasetOrigin origin;
  private boolean locked = false;
  private boolean privat = false;
  private UUID gbifKey;
  private UUID gbifPublisherKey;
  private LocalDateTime imported; // from import table
  private LocalDateTime deleted;

  // human metadata
  @NotNull @NotBlank
  private String title;
  private String alias;
  private String description;
  private List<String> organisations = Lists.newArrayList();
  private String contact;
  private List<String> authorsAndEditors = Lists.newArrayList();
  private License license;
  private String version;
  private LocalDate released;
  private String citation;
  private String geographicScope;
  @AbsoluteURI
  private URI website;
  @AbsoluteURI
  private URI logo;
  private String group;
  @Min(1) @Max(5)
  private Integer confidence;
  @Min(0) @Max(100)
  private Integer completeness;
  private String notes;

  // human metadata
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Integer size;
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Set<Integer> contributesTo;

  // security
  @JsonIgnore
  private IntSet editors = new IntOpenHashSet();

  /**
   * Applies a dataset metadata patch, setting all non null fields
   * @param patch
   */
  public void apply(DatasetMetadata patch) {
    // copy all properties that are not null
    try {
      for (PropertyDescriptor prop : METADATA_PROPS){
        Object val = prop.getReadMethod().invoke(patch);
        if (val != null) {
          prop.getWriteMethod().invoke(this, val);
        }
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Integer getKey() {
    return key;
  }
  
  public DatasetType getType() {
    return type;
  }
  
  public void setType(DatasetType type) {
    this.type = type;
  }
  
  @Override
  public void setKey(Integer key) {
    this.key = key;
  }

  public Integer getSourceKey() {
    return sourceKey;
  }

  public void setSourceKey(Integer sourceKey) {
    this.sourceKey = sourceKey;
  }

  /**
   * @return the last successful import attempt that created the current data in the data partitions
   */
  public Integer getImportAttempt() {
    return importAttempt;
  }

  public void setImportAttempt(Integer importAttempt) {
    this.importAttempt = importAttempt;
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
  
  @Override
  public List<String> getAuthorsAndEditors() {
    return authorsAndEditors;
  }
  
  @Override
  public void setAuthorsAndEditors(List<String> authorsAndEditors) {
    this.authorsAndEditors = authorsAndEditors;
  }
  
  @Override
  public List<String> getOrganisations() {
    return organisations;
  }
  
  @Override
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
  
  @Override
  public License getLicense() {
    return license;
  }
  
  @Override
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
  public String getGeographicScope() {
    return geographicScope;
  }
  
  @Override
  public void setGeographicScope(String geographicScope) {
    this.geographicScope = geographicScope;
  }
  
  /**
   * Release date of the source data.
   * The date can usually only be taken from metadata explicitly given by the source.
   */
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
  
  public DatasetOrigin getOrigin() {
    return origin;
  }
  
  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }
  
  public boolean isLocked() {
    return locked;
  }

  @JsonProperty("private")
  public boolean isPrivat() {
    return privat;
  }

  public void setPrivat(boolean privat) {
    this.privat = privat;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
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
  
  /**
   * If the dataset participates in any catalogue assemblies
   * this is indicated here by listing the catalogues datasetKey.
   * <p>
   * Dataset used to build the provisional catalogue will be trusted and insert their names into the names index.
   */
  public Set<Integer> getContributesTo() {
    return contributesTo;
  }
  
  public void setContributesTo(Set<Integer> contributesTo) {
    this.contributesTo = contributesTo;
  }
  
  public void addContributesTo(Integer catalogueKey) {
    if (contributesTo == null) {
      contributesTo = new HashSet<>();
    }
    contributesTo.add(catalogueKey);
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
  public String getAlias() {
    return alias;
  }
  
  @Override
  public void setAlias(String alias) {
    this.alias = alias;
  }
  
  @Override
  public String getGroup() {
    return group;
  }
  
  @Override
  public void setGroup(String group) {
    this.group = group;
  }
  
  @Override
  public Integer getConfidence() {
    return confidence;
  }
  
  @Override
  public void setConfidence(Integer confidence) {
    this.confidence = confidence;
  }
  
  @Override
  public Integer getCompleteness() {
    return completeness;
  }
  
  @Override
  public void setCompleteness(Integer completeness) {
    this.completeness = completeness;
  }



  public IntSet getEditors() {
    return editors;
  }

  public void setEditors(IntSet editors) {
    this.editors = editors == null ? new IntOpenHashSet() : editors;
  }

  public void addEditor(int userKey) {
    editors.add(userKey);
  }

  public void removeEditor(int userKey) {
    if (getCreatedBy() != null && userKey == (int) getCreatedBy()) {
      throw new IllegalArgumentException("Original dataset creator cannot be removed from editors");
    }
    editors.remove(userKey);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Dataset)) return false;
    if (!super.equals(o)) return false;
    Dataset dataset = (Dataset) o;
    return locked == dataset.locked &&
      privat == dataset.privat &&
      Objects.equals(key, dataset.key) &&
      Objects.equals(sourceKey, dataset.sourceKey) &&
      Objects.equals(importAttempt, dataset.importAttempt) &&
      type == dataset.type &&
      origin == dataset.origin &&
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
      Objects.equals(geographicScope, dataset.geographicScope) &&
      Objects.equals(website, dataset.website) &&
      Objects.equals(group, dataset.group) &&
      Objects.equals(logo, dataset.logo) &&
      Objects.equals(size, dataset.size) &&
      Objects.equals(confidence, dataset.confidence) &&
      Objects.equals(completeness, dataset.completeness) &&
      Objects.equals(notes, dataset.notes) &&
      Objects.equals(contributesTo, dataset.contributesTo) &&
      Objects.equals(imported, dataset.imported) &&
      Objects.equals(deleted, dataset.deleted) &&
      Objects.equals(editors, dataset.editors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, sourceKey, importAttempt, type, origin, locked, privat, title, alias, gbifKey, gbifPublisherKey, description, organisations, contact, authorsAndEditors, license, version, released, citation, geographicScope, website, group, logo, size, confidence, completeness, notes, contributesTo, imported, deleted, editors);
  }

  @Override
  public String toString() {
    return "Dataset " + key + ": " + title;
  }
}
