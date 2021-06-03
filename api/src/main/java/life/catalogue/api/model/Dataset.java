package life.catalogue.api.model;

import life.catalogue.api.constraints.AbsoluteURI;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata about a dataset which can be archived
 */
public class Dataset extends DataEntity<Integer> {
  public static final Map<String, Object> NULL_TYPES;
  // properties which are human mediated and can be patched
  public static final List<PropertyDescriptor> PATCH_PROPS;

  static {
    try {
      Set<String> exclude = Set.of(
        "key",
        "sourceKey",
        "privat",
        "type",
        "origin",
        "importAttempt",
        "imported",
        "deleted",
        "gbifKey",
        "gbifPublisherKey",
        "size",
        "notes",
        "aliasOrTitle"
      );
      PATCH_PROPS = Arrays.stream(Introspector.getBeanInfo(Dataset.class).getPropertyDescriptors())
                          .filter(p -> !exclude.contains(p.getName()) && p.getWriteMethod() != null)
                          .collect(Collectors.toUnmodifiableList());

      Map<String, Object> nullTypes = new HashMap<>();
      for (PropertyDescriptor p : PATCH_PROPS) {
        Object nullType = null;
        if (p.getPropertyType().equals(URI.class)) {
          nullType = URI.create("null:null");
        } else if (p.getPropertyType().equals(String.class)) {
          nullType = "";
        } else if (p.getPropertyType().equals(Integer.class)) {
          nullType = Integer.MIN_VALUE;
        } else if (p.getPropertyType().equals(Agent.class)) {
          nullType = new Agent("null");
        } else if (p.getPropertyType().equals(LocalDate.class)) {
          nullType = LocalDate.of(1900, 1, 1);
        }
        if (nullType != null) {
          nullTypes.put(p.getName(), nullType);
        }
      }
      NULL_TYPES = Map.copyOf(nullTypes);

    } catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }
  }

  // internal keys & flags
  private Integer key;
  private Integer sourceKey;
  private boolean privat = false;
  @NotNull
  private DatasetType type;
  @NotNull
  private DatasetOrigin origin;
  private Integer attempt;
  private LocalDateTime imported; // from import table
  private LocalDateTime deleted;
  private UUID gbifKey;
  private UUID gbifPublisherKey;
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Integer size;
  private String notes;

  // human metadata
  private DOI doi;
  private Map<String, String> identifier = new HashMap<>();
  @NotNull
  @NotBlank
  private String title;
  private String alias;
  private String description;
  private LocalDate issued;
  private String version;
  private String issn;
  private Agent contact;
  private List<Agent> creator;
  private List<Agent> editor;
  private Agent publisher;
  private List<Agent> contributor;
  private List<Agent> distributor;
  private String geographicScope;
  private String taxonomicScope;
  private String temporalScope;
  @Min(1)
  @Max(5)
  private Integer confidence;
  @Min(0)
  @Max(100)
  private Integer completeness;
  private License license;
  @AbsoluteURI
  private URI url;
  @AbsoluteURI
  private URI logo;
  private List<Citation> source;

  public Dataset() {
  }

  public Dataset(Dataset other) {
    super(other);
    this.key = other.key;
    this.sourceKey = other.sourceKey;
    this.privat = other.privat;
    this.type = other.type;
    this.origin = other.origin;
    this.attempt = other.attempt;
    this.imported = other.imported;
    this.deleted = other.deleted;
    this.gbifKey = other.gbifKey;
    this.gbifPublisherKey = other.gbifPublisherKey;
    this.size = other.size;
    this.notes = other.notes;
    this.doi = other.doi;
    this.identifier = other.identifier;
    this.title = other.title;
    this.alias = other.alias;
    this.description = other.description;
    this.issued = other.issued;
    this.version = other.version;
    this.issn = other.issn;
    this.contact = other.contact;
    this.creator = other.creator;
    this.editor = other.editor;
    this.publisher = other.publisher;
    this.contributor = other.contributor;
    this.distributor = other.distributor;
    this.geographicScope = other.geographicScope;
    this.taxonomicScope = other.taxonomicScope;
    this.temporalScope = other.temporalScope;
    this.confidence = other.confidence;
    this.completeness = other.completeness;
    this.license = other.license;
    this.url = other.url;
    this.logo = other.logo;
    this.source = other.source;
  }

  /**
   * Applies a dataset metadata patch, setting all non null fields.
   * In order for a patch to specify that a certain field should become NULL, we need to define other values than null which means "do not patch".
   * Depending on the data type these are:
   * Integer: -1
   * String: ""
   * Collection: empty collection
   * License: never null
   * LocalDate: 1900-01-01
   * Person: ???
   * URI: ???
   *
   * @param patch
   */
  public void applyPatch(Dataset patch) {
    // copy all properties that are not null
    try {
      for (PropertyDescriptor prop : PATCH_PROPS) {
        Object val = prop.getReadMethod().invoke(patch);
        if (val != null) {
          if (NULL_TYPES.containsKey(prop.getName())) {
            Object nullType = NULL_TYPES.get(prop.getName());
            val = val.equals(nullType) ? null : val;
          }
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
  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public DOI getDoi() {
    return doi;
  }

  public void setDoi(DOI doi) {
    this.doi = doi;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<Agent> getCreator() {
    return creator;
  }

  public void setCreator(List<Agent> creator) {
    this.creator = creator;
  }

  public void addCreator(Agent author) {
    if (creator == null) {
      creator = new ArrayList<>();
    }
    creator.add(author);
  }

  public List<Agent> getEditor() {
    return editor;
  }

  public void setEditor(List<Agent> editor) {
    this.editor = editor;
  }

  public void addEditor(Agent editor) {
    if (this.editor == null) {
      this.editor = new ArrayList<>();
    }
    this.editor.add(editor);
  }

  @JsonIgnore
  public String getAliasOrTitle() {
    return ObjectUtils.coalesce(alias, title);
  }

  @JsonIgnore
  public boolean hasDeletedDate() {
    return deleted != null;
  }


  @JsonProperty("private")
  public boolean isPrivat() {
    return privat;
  }

  public void setPrivat(boolean privat) {
    this.privat = privat;
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

  public DatasetOrigin getOrigin() {
    return origin;
  }

  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }

  public LocalDateTime getDeleted() {
    return deleted;
  }

  public void setDeleted(LocalDateTime deleted) {
    this.deleted = deleted;
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

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Map<String, String> getIdentifier() {
    return identifier;
  }

  public void setIdentifier(Map<String, String> identifier) {
    this.identifier = identifier;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public Agent getContact() {
    return contact;
  }

  public void setContact(Agent contact) {
    this.contact = contact;
  }

  public Agent getPublisher() {
    return publisher;
  }

  public void setPublisher(Agent publisher) {
    this.publisher = publisher;
  }

  public List<Agent> getContributor() {
    return contributor;
  }

  public void setContributor(List<Agent> contributor) {
    this.contributor = contributor;
  }

  public List<Agent> getDistributor() {
    return distributor;
  }

  public void setDistributor(List<Agent> distributor) {
    this.distributor = distributor;
  }

  public String getGeographicScope() {
    return geographicScope;
  }

  public void setGeographicScope(String geographicScope) {
    this.geographicScope = geographicScope;
  }

  public String getTaxonomicScope() {
    return taxonomicScope;
  }

  public void setTaxonomicScope(String taxonomicScope) {
    this.taxonomicScope = taxonomicScope;
  }

  public String getTemporalScope() {
    return temporalScope;
  }

  public void setTemporalScope(String temporalScope) {
    this.temporalScope = temporalScope;
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

  public LocalDate getIssued() {
    return issued;
  }

  public void setIssued(LocalDate issued) {
    this.issued = issued;
  }

  public URI getUrl() {
    return url;
  }

  public void setUrl(URI url) {
    this.url = url;
  }

  public URI getLogo() {
    return logo;
  }

  public void setLogo(URI logo) {
    this.logo = logo;
  }

  public List<Citation> getSource() {
    return source;
  }

  public void setSource(List<Citation> source) {
    this.source = source;
  }

  public String getIssn() {
    return issn;
  }

  public void setIssn(String issn) {
    this.issn = issn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Dataset)) return false;
    if (!super.equals(o)) return false;
    Dataset dataset = (Dataset) o;
    return privat == dataset.privat
           && Objects.equals(key, dataset.key)
           && Objects.equals(sourceKey, dataset.sourceKey)
           && type == dataset.type
           && origin == dataset.origin
           && Objects.equals(attempt, dataset.attempt)
           && Objects.equals(imported, dataset.imported)
           && Objects.equals(deleted, dataset.deleted)
           && Objects.equals(gbifKey, dataset.gbifKey)
           && Objects.equals(gbifPublisherKey, dataset.gbifPublisherKey)
           && Objects.equals(size, dataset.size)
           && Objects.equals(notes, dataset.notes)
           && Objects.equals(doi, dataset.doi)
           && Objects.equals(identifier, dataset.identifier)
           && Objects.equals(title, dataset.title)
           && Objects.equals(alias, dataset.alias)
           && Objects.equals(description, dataset.description)
           && Objects.equals(issued, dataset.issued)
           && Objects.equals(version, dataset.version)
           && Objects.equals(issn, dataset.issn)
           && Objects.equals(contact, dataset.contact)
           && Objects.equals(creator, dataset.creator)
           && Objects.equals(editor, dataset.editor)
           && Objects.equals(publisher, dataset.publisher)
           && Objects.equals(contributor, dataset.contributor)
           && Objects.equals(distributor, dataset.distributor)
           && Objects.equals(geographicScope, dataset.geographicScope)
           && Objects.equals(taxonomicScope, dataset.taxonomicScope)
           && Objects.equals(temporalScope, dataset.temporalScope)
           && Objects.equals(confidence, dataset.confidence)
           && Objects.equals(completeness, dataset.completeness)
           && license == dataset.license
           && Objects.equals(url, dataset.url)
           && Objects.equals(logo, dataset.logo)
           && Objects.equals(source, dataset.source);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, sourceKey, privat, type, origin, attempt, imported, deleted, gbifKey, gbifPublisherKey, size, notes, doi, identifier, title, alias, description, issued, version, issn, contact, creator, editor, publisher, contributor, distributor, geographicScope, taxonomicScope, temporalScope, confidence, completeness, license, url, logo, source);
  }

  @Override
  public String toString() {
    return "Dataset " + key + ": " + attempt;
  }

}
