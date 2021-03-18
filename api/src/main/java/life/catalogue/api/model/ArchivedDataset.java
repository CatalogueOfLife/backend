package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import life.catalogue.api.constraints.AbsoluteURI;
import life.catalogue.api.util.ObjectUtils;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Metadata about a dataset or a subset of it if parentKey is given.
 */
public class ArchivedDataset extends DataEntity<Integer> implements DatasetMetadata {
  private static final List<PropertyDescriptor> METADATA_PROPS;
  static {
    try {
      Set<String> metadata = Arrays.stream(Introspector.getBeanInfo(DatasetMetadata.class).getMethodDescriptors())
        .map(m -> m.getMethod().getName())
        .collect(Collectors.toSet());
      metadata.remove("getKey");
      METADATA_PROPS = Arrays.stream(Introspector.getBeanInfo(DatasetMetadata.class).getPropertyDescriptors())
        .filter(p -> metadata.contains(p.getReadMethod().getName()))
        .collect(Collectors.toUnmodifiableList());

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

  // human metadata
  @NotNull @NotBlank
  private String title;
  private String alias;
  private String description;
  private List<Organisation> organisations = Lists.newArrayList();
  private Person contact;
  private List<Person> authors = Lists.newArrayList();
  private List<Person> editors = Lists.newArrayList();
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

  public ArchivedDataset() {
  }

  public ArchivedDataset(ArchivedDataset other) {
    super(other);
    this.key = other.key;
    this.sourceKey = other.sourceKey;
    this.importAttempt = other.importAttempt;
    this.type = other.type;
    this.origin = other.origin;
    this.title = other.title;
    this.alias = other.alias;
    this.description = other.description;
    this.organisations = other.organisations;
    this.contact = other.contact;
    this.authors = other.authors;
    this.editors = other.editors;
    this.license = other.license;
    this.version = other.version;
    this.released = other.released;
    this.citation = other.citation;
    this.geographicScope = other.geographicScope;
    this.website = other.website;
    this.logo = other.logo;
    this.group = other.group;
    this.confidence = other.confidence;
    this.completeness = other.completeness;
    this.notes = other.notes;
  }

  /**
   * Applies a dataset metadata patch, setting all non null fields
   * @param patch
   */
  public void applyPatch(DatasetMetadata patch) {
    // copy all properties that are not null
    try {
      for (PropertyDescriptor prop : METADATA_PROPS){
        Object val = prop.getReadMethod().invoke(patch);
        if (val != null && !(val instanceof Collection && ((Collection)val).isEmpty())) {
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

  @Override
  public String getDescription() {
    return description;
  }
  
  @Override
  public void setDescription(String description) {
    this.description = description;
  }

  public List<Person> getAuthors() {
    return authors;
  }

  public void setAuthors(List<Person> authors) {
    this.authors = authors;
  }

  public List<Person> getEditors() {
    return editors;
  }

  public void setEditors(List<Person> editors) {
    this.editors = editors;
  }

  @Override
  public List<Organisation> getOrganisations() {
    return organisations;
  }
  
  @Override
  public void setOrganisations(List<Organisation> organisations) {
    this.organisations = organisations;
  }
  
  @Override
  public Person getContact() {
    return contact;
  }
  
  @Override
  public void setContact(Person contact) {
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

  public String getNotes() {
    return notes;
  }
  
  public void setNotes(String notes) {
    this.notes = notes;
  }

  @JsonIgnore
  public String getAliasOrTitle() {
    return ObjectUtils.coalesce(alias, title);
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

  public void setTaxonomicScope(String scope) {
    setGroup(scope);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArchivedDataset)) return false;
    if (!super.equals(o)) return false;
    ArchivedDataset that = (ArchivedDataset) o;
    return Objects.equals(key, that.key) &&
      Objects.equals(sourceKey, that.sourceKey) &&
      Objects.equals(importAttempt, that.importAttempt) &&
      type == that.type &&
      origin == that.origin &&
      Objects.equals(title, that.title) &&
      Objects.equals(alias, that.alias) &&
      Objects.equals(description, that.description) &&
      Objects.equals(organisations, that.organisations) &&
      Objects.equals(contact, that.contact) &&
      Objects.equals(authors, that.authors) &&
      Objects.equals(editors, that.editors) &&
      license == that.license &&
      Objects.equals(version, that.version) &&
      Objects.equals(released, that.released) &&
      Objects.equals(citation, that.citation) &&
      Objects.equals(geographicScope, that.geographicScope) &&
      Objects.equals(website, that.website) &&
      Objects.equals(logo, that.logo) &&
      Objects.equals(group, that.group) &&
      Objects.equals(confidence, that.confidence) &&
      Objects.equals(completeness, that.completeness) &&
      Objects.equals(notes, that.notes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, sourceKey, importAttempt, type, origin, title, alias, description, organisations, contact, authors, editors,
      license, version, released, citation, geographicScope, website, logo, group, confidence, completeness, notes);
  }

  @Override
  public String toString() {
    return "ArchivedDataset " + key + ": " + importAttempt;
  }
}
