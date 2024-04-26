package life.catalogue.api.model;

import life.catalogue.api.constraints.AbsoluteURI;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.License;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.util.YamlUtils;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLType;

/**
 * Metadata about a dataset which can be archived
 */
public class Dataset extends DataEntity<Integer> {
  // key=json field name, value=java type specific instance which is considered to be null, but can be stored and moved around without loss
  // only used for internal representation of explicit nulls for patches!!!
  public static final Map<String, Object> NULL_TYPES;
  // properties which are human mediated and can be patched
  // title is the only required property, make sure it is not null !!!
  public static final List<PropertyDescriptor> PATCH_PROPS;

  static {
    try {
      Set<String> exclude = Set.of(
        "key",
        "attempt",
        "sourceKey",
        "privat",
        "type",
        "origin",
        "imported",
        "deleted",
        "lastImportAttempt",
        "lastImportState",
        "gbifKey",
        "gbifPublisherKey",
        "size",
        "notes",
        "aliasOrTitle",
        "created",
        "createdBy",
        "modified",
        "modifiedBy"
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
        } else if (p.getPropertyType().equals(DOI.class)) {
          nullType = new DOI("10.0000", "null");;
        } else if (p.getPropertyType().equals(Agent.class)) {
          nullType = Agent.person("0000-0000-0000-0000");
        } else if (p.getPropertyType().equals(LocalDate.class)) {
          nullType = LocalDate.of(1900, 1, 1);
        } else if (p.getPropertyType().equals(FuzzyDate.class)) {
          nullType = FuzzyDate.of(0);
        } else if (p.getPropertyType().equals(List.class)) {
          var l = new ArrayList<>();
          l.add(null);
          l.add(null);
          nullType = l;
        } else if (p.getPropertyType().equals(Map.class)) {
          var l = new HashMap<>();
          l.put(null ,null);
          nullType = l;
        }
        // unupported as they are required
        // License

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
  private Integer attempt; // last successful import attempt that created the current data
  private LocalDateTime imported; // linked via attempt from import table
  private LocalDateTime lastImportAttempt; // last try to import the dataset, will be set even for unchanged attempts
  // state of the last import that was not unchanged.
  // Does not have to correlate with the lastImportAttempt timestamp
  private ImportState lastImportState;
  private LocalDateTime deleted;
  private UUID gbifKey;
  private UUID gbifPublisherKey;
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Integer size;

  // human metadata
  private DOI doi;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, String> identifier = new HashMap<>();
  @NotNull
  @NotBlank
  private String title;
  private String alias;
  private String description;
  private FuzzyDate issued;
  private String version;
  private String issn;
  @Valid
  private Agent contact;
  @Valid
  private List<Agent> creator;
  @Valid
  private List<Agent> editor;
  @Valid
  private Agent publisher;
  @Valid
  private List<Agent> contributor;
  private List<String> keyword = new ArrayList<>();
  private Integer containerKey;
  private String containerTitle;
  @Valid
  private List<Agent> containerCreator;
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
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, String> urlFormatter = new HashMap<>();
  @Valid
  private List<Citation> source = new ArrayList<>();
  private String notes;
  private String _citation; // cache field
  private String _citationText; // cache field

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
    this.lastImportAttempt = other.lastImportAttempt;
    this.lastImportState = other.lastImportState;
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
    this.keyword = other.keyword;
    this.containerKey = other.containerKey;
    this.containerTitle = other.containerTitle;
    this.containerCreator = other.containerCreator;
    this.geographicScope = other.geographicScope;
    this.taxonomicScope = other.taxonomicScope;
    this.temporalScope = other.temporalScope;
    this.confidence = other.confidence;
    this.completeness = other.completeness;
    this.license = other.license;
    this.url = other.url;
    this.logo = other.logo;
    this.urlFormatter = other.urlFormatter;
    this.source = other.source;
  }

  public static Dataset read(InputStream in){
    try {
      return YamlUtils.read(Dataset.class, in);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  public CSLItemData toCSL() {
    CSLItemDataBuilder builder = new CSLItemDataBuilder();
    builder
      .type(CSLType.DATASET)
      .shortTitle(alias)
      .title(title)
      .version(version)
      .ISSN(issn);
    if (key != null) {
      builder.id(key.toString());
    }
    if (keyword != null && !keyword.isEmpty()) {
      builder.keyword(String.join(", ", keyword));
    }
    if (containerTitle != null) {
      builder
        .type(CSLType.CHAPTER)
        .author(toNamesArray(unique(merge(creator, editor))))
        .containerTitle(containerTitle)
        .containerAuthor(toNamesArray(containerCreator));
    } else {
      builder
        .author(toNamesArray(creator))
        .editor(toNamesArray(editor));
    }
    if (doi != null) {
      builder.DOI(doi.toString());
    }
    if (url != null) {
      builder.URL(url.toString());
    }
    if (issued != null) {
      builder.issued(issued.toCSLDate());
    }
    if (publisher != null && publisher.getOrganisation() != null) {
      builder.publisher(publisher.getOrganisation());
      builder.publisherPlace(publisher.getAddress());
    }
    // no license, distributor, contributor
    return builder.build();
  }

  public Citation toCitation() {
    Citation c = new Citation();
    if (key != null) {
      c.setId(key.toString());
    }
    c.setType(CSLType.DATASET);
    c.setTitle(title);
    c.setIssued(issued);
    c.setVersion(version);
    c.setIssn(issn);;
    c.setDoi(doi);
    if (containerTitle != null) {
      c.setType(CSLType.CHAPTER);
      c.setAuthor(toNames(unique(merge(creator, editor))));
      c.setContainerTitle(containerTitle);
      c.setContainerAuthor(toNames(containerCreator));
    } else {
      c.setAuthor(toNames(creator));
      c.setEditor(toNames(editor));
    }
    if (url != null) {
      c.setUrl(url.toString());
    }
    if (publisher != null && publisher.getOrganisation() != null) {
      c.setPublisher(publisher.getOrganisation());
      c.setPublisherPlace(publisher.getAddress());
    }
    // no license, distributor, contributor
    return c;
  }

  static List<Agent> merge(List<Agent>... names) {
    List<Agent> all = new ArrayList<>();
    for (List<Agent> n : names) {
      if (n != null && !n.isEmpty()) {
        all.addAll(n);
      }
    }
    return all;
  }

  static List<Agent> unique(List<Agent> names) {
    final Set<String> seen = ConcurrentHashMap.newKeySet();
    names.removeIf(n -> {
      if (n != null && n.getName() != null && !seen.contains(n.getName())) {
        seen.add(n.getName());
        return false;
      }
      return true;
    });
    return names;
  }

  private static CSLName[] toNamesArray(List<Agent> names) {
    if (names == null || names.isEmpty()) return null;
    return names.stream()
                .map(Agent::toCSL)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                .toArray(CSLName[]::new);
  }

  private static List<CslName> toNames(List<Agent> names) {
    if (names == null || names.isEmpty()) return null;
    return names.stream()
                .map(Agent::toCsl)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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

  public LocalDateTime getLastImportAttempt() {
    return lastImportAttempt;
  }

  public void setLastImportAttempt(LocalDateTime lastImportAttempt) {
    this.lastImportAttempt = lastImportAttempt;
  }

  public ImportState getLastImportState() {
    return lastImportState;
  }

  public void setLastImportState(ImportState lastImportState) {
    this.lastImportState = lastImportState;
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

  public String getLabel() {
    StringBuilder sb = new StringBuilder();
    sb.append(title);
    if (version != null) {
      sb.append(" (");
      sb.append(version);
      sb.append(")");
    }
    return sb.toString();
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

  public Integer getContainerKey() {
    return containerKey;
  }

  public void setContainerKey(Integer containerKey) {
    this.containerKey = containerKey;
  }

  public String getContainerTitle() {
    return containerTitle;
  }

  public void setContainerTitle(String containerTitle) {
    this.containerTitle = containerTitle;
  }

  public List<Agent> getContainerCreator() {
    return containerCreator;
  }

  public void setContainerCreator(List<Agent> containerCreator) {
    this.containerCreator = containerCreator;
  }

  @JsonIgnore
  public String getAliasOrTitle() {
    return ObjectUtils.coalesce(alias, title);
  }

  @JsonIgnore
  public boolean hasDeletedDate() {
    return deleted != null;
  }

  public void processAllAgents(Consumer<Agent> processor){
    processAgent(contact, processor);
    processAllAgents(creator, processor);
    processAllAgents(editor, processor);
    processAgent(publisher, processor);
    processAllAgents(contributor, processor);
    processAllAgents(containerCreator, processor);
  }

  private void processAgent(Agent agent, Consumer<Agent> processor){
    if (agent != null) {
      processor.accept(agent);
    }
  }

  private void processAllAgents(List<Agent> agents, Consumer<Agent> processor){
    if (agents != null) {
      agents.forEach(processor);
    }
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

  public void appendNotes(String notes) {
    if (this.notes == null) {
      this.notes = notes;
    } else {
      this.notes = this.notes + " " + notes;
    }
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

  public void addContributor(Agent contributor) {
    if (contributor != null) {
      if (this.contributor == null) {
        this.contributor = new ArrayList<>();
      }
      this.contributor.add(contributor);
    }
  }

  public List<String> getKeyword() {
    return keyword;
  }

  public void setKeyword(List<String> keyword) {
    this.keyword = keyword;
  }

  public void addKeyword(String keyword) {
    if (keyword != null) {
      if (this.keyword == null) {
        this.keyword = new ArrayList<>();
      }
      this.keyword.add(keyword);
    }
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
    return ObjectUtils.coalesceLazy(version, () -> issued == null ? null : issued.toString());
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public FuzzyDate getIssued() {
    return issued;
  }

  public void setIssued(FuzzyDate issued) {
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

  public Map<String, String> getUrlFormatter() {
    return urlFormatter;
  }

  public void setUrlFormatter(Map<String, String> urlFormatter) {
    this.urlFormatter = urlFormatter;
  }

  public List<Citation> getSource() {
    return source;
  }

  public void setSource(List<Citation> source) {
    this.source = source;
  }

  public void addSource(Citation citation) {
    if (citation != null) {
      if (source == null) {
        source = new ArrayList<>();
      }
      source.add(citation);
    }
  }

  public String getIssn() {
    return issn;
  }

  public void setIssn(String issn) {
    this.issn = issn;
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String getCitation() {
    if (_citation == null) {
      _citation = CslUtil.buildCitationHtml(toCSL());
    }
    return _citation;
  }

  @JsonIgnore
  public String getCitationText() {
    if (_citationText == null) {
      _citationText = CslUtil.buildCitation(toCSL());
    }
    return _citationText;
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
           && Objects.equals(lastImportAttempt, dataset.lastImportAttempt)
           && Objects.equals(lastImportState, dataset.lastImportState)
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
           && Objects.equals(keyword, dataset.keyword)
           && Objects.equals(containerKey, dataset.containerKey)
           && Objects.equals(containerTitle, dataset.containerTitle)
           && Objects.equals(containerCreator, dataset.containerCreator)
           && Objects.equals(geographicScope, dataset.geographicScope)
           && Objects.equals(taxonomicScope, dataset.taxonomicScope)
           && Objects.equals(temporalScope, dataset.temporalScope)
           && Objects.equals(confidence, dataset.confidence)
           && Objects.equals(completeness, dataset.completeness)
           && license == dataset.license
           && Objects.equals(url, dataset.url)
           && Objects.equals(logo, dataset.logo)
           && Objects.equals(urlFormatter, dataset.urlFormatter)
           && Objects.equals(source, dataset.source);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, sourceKey, privat, type, origin,
      attempt, lastImportAttempt, lastImportState, imported, deleted,
      gbifKey, gbifPublisherKey, size, notes,
      doi, identifier, title, alias, description, issued, version, issn, contact, creator, editor, publisher, contributor, keyword,
      containerKey, containerTitle, containerCreator,
      geographicScope, taxonomicScope, temporalScope, confidence, completeness, license, url, logo, urlFormatter, source);
  }

  @Override
  public String toString() {
    return "Dataset " + key + ": " + attempt;
  }
}
