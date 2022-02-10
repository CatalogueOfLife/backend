package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.DOI;
import life.catalogue.api.util.ObjectUtils;

import java.time.Year;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;


/**
 * All metadata attributes available in a DataCite Rest DOI.
 */
public class DoiAttributes {

  public static String VERSION = "http://datacite.org/schema/kernel-4";
  public static final Map<String, String> DATASET_RESOURCE = Map.of("resourceTypeGeneral", "Dataset",  "resourceType", "Dataset");

  @NotNull
  private DOI doi;

  private EventType event;
  /**
   * The identifiers array is a combination of identifier and alternate_identifier and there is always one of type DOI.
   * We include the doi above in this list.
   */
  @NotNull
  private List<Identifier> identifiers;
  @NotNull
  private List<Creator> creators;
  @NotNull
  private List<Title> titles;
  @NotNull
  private String publisher;

  // container - read only
  @NotNull
  private Integer publicationYear;

  private List<Subject> subjects;
  private List<Contributor> contributors;
  private List<Date> dates;
  private String language;
  private Map<String, String> types;
  private List<RelatedIdentifier> relatedIdentifiers;
  private List<String> sizes;
  private List<String> formats;
  private String version;
  private String schemaVersion;
  private List<Rights> rightsList;
  private List<Description> descriptions;
  private List<GeoLocation> geoLocations;
  // fundingReferences
  @NotNull
  private String url; // the target URL
  private List<String> contentUrl;
  private Float metadataVersion;
  private String source;
  private DoiState state;
  private String reason;
  private String created;
  private String registered;
  private String updated;

  public static DoiAttributes required(DOI doi, String title, List<Creator> creator, String url, @Nullable String publisher, @Nullable Integer publicationYear) {
    DoiAttributes attr = new DoiAttributes(doi);
    attr.setTypes(DATASET_RESOURCE);
    attr.setTitles(List.of(new Title(title)));
    attr.setCreators(creator);
    attr.setUrl(url);
    attr.setPublisher(ObjectUtils.coalesce(publisher, "GBIF"));
    attr.setPublicationYear(ObjectUtils.coalesce(publicationYear, Year.now().get(ChronoField.YEAR)));
    return attr;
  }

  public DoiAttributes() {
  }

  public DoiAttributes(@NotNull DOI doi) {
    this.doi = doi;
    setTypes(DATASET_RESOURCE);
  }

  public DOI getDoi() {
    return doi;
  }

  public void setDoi(DOI doi) {
    this.doi = doi;
  }

  public String getPrefix() {
    return doi.getPrefix();
  }

  public String getSuffix() {
    return doi.getSuffix();
  }

  public EventType getEvent() {
    return event;
  }

  public void setEvent(EventType event) {
    this.event = event;
  }

  public List<Identifier> getIdentifiers() {
    return identifiers;
  }

  public void setIdentifiers(List<Identifier> identifiers) {
    this.identifiers = identifiers;
  }

  public List<Creator> getCreators() {
    return creators;
  }

  public void setCreators(List<Creator> creators) {
    this.creators = creators;
  }

  public List<Title> getTitles() {
    return titles;
  }

  public void setTitles(List<Title> titles) {
    this.titles = titles;
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  public Integer getPublicationYear() {
    return publicationYear;
  }

  public void setPublicationYear(Integer publicationYear) {
    this.publicationYear = publicationYear;
  }

  public List<Subject> getSubjects() {
    return subjects;
  }

  public void setSubjects(List<Subject> subjects) {
    this.subjects = subjects;
  }

  public List<Contributor> getContributors() {
    return contributors;
  }

  public void setContributors(List<Contributor> contributors) {
    this.contributors = contributors;
  }

  public List<Date> getDates() {
    return dates;
  }

  public void setDates(List<Date> dates) {
    this.dates = dates;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public Map<String, String> getTypes() {
    return types;
  }

  public void setTypes(Map<String, String> types) {
    this.types = types;
  }

  public List<RelatedIdentifier> getRelatedIdentifiers() {
    return relatedIdentifiers;
  }

  public void setRelatedIdentifiers(List<RelatedIdentifier> relatedIdentifiers) {
    this.relatedIdentifiers = relatedIdentifiers;
  }

  public List<String> getSizes() {
    return sizes;
  }

  public void setSizes(List<String> sizes) {
    this.sizes = sizes;
  }

  public List<String> getFormats() {
    return formats;
  }

  public void setFormats(List<String> formats) {
    this.formats = formats;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public List<Rights> getRightsList() {
    return rightsList;
  }

  public void setRightsList(List<Rights> rightsList) {
    this.rightsList = rightsList;
  }

  public List<Description> getDescriptions() {
    return descriptions;
  }

  public void setDescriptions(List<Description> descriptions) {
    this.descriptions = descriptions;
  }

  public List<GeoLocation> getGeoLocations() {
    return geoLocations;
  }

  public void setGeoLocations(List<GeoLocation> geoLocations) {
    this.geoLocations = geoLocations;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public List<String> getContentUrl() {
    return contentUrl;
  }

  public void setContentUrl(List<String> contentUrl) {
    this.contentUrl = contentUrl;
  }

  public Float getMetadataVersion() {
    return metadataVersion;
  }

  public void setMetadataVersion(Float metadataVersion) {
    this.metadataVersion = metadataVersion;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public DoiState getState() {
    return state;
  }

  public void setState(DoiState state) {
    this.state = state;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(String created) {
    this.created = created;
  }

  public String getRegistered() {
    return registered;
  }

  public void setRegistered(String registered) {
    this.registered = registered;
  }

  public String getUpdated() {
    return updated;
  }

  public void setUpdated(String updated) {
    this.updated = updated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DoiAttributes)) return false;
    DoiAttributes that = (DoiAttributes) o;
    return Objects.equals(doi, that.doi)
           && event == that.event
           && Objects.equals(identifiers, that.identifiers)
           && Objects.equals(creators, that.creators)
           && Objects.equals(titles, that.titles)
           && Objects.equals(publisher, that.publisher)
           && Objects.equals(publicationYear, that.publicationYear)
           && Objects.equals(subjects, that.subjects)
           && Objects.equals(contributors, that.contributors)
           && Objects.equals(dates, that.dates)
           && Objects.equals(language, that.language)
           && Objects.equals(types, that.types)
           && Objects.equals(relatedIdentifiers, that.relatedIdentifiers)
           && Objects.equals(sizes, that.sizes)
           && Objects.equals(formats, that.formats)
           && Objects.equals(version, that.version)
           && Objects.equals(schemaVersion, that.schemaVersion)
           && Objects.equals(rightsList, that.rightsList)
           && Objects.equals(descriptions, that.descriptions)
           && Objects.equals(geoLocations, that.geoLocations)
           && Objects.equals(url, that.url)
           && Objects.equals(contentUrl, that.contentUrl)
           && Objects.equals(metadataVersion, that.metadataVersion)
           && Objects.equals(source, that.source)
           && state == that.state
           && Objects.equals(reason, that.reason)
           && Objects.equals(created, that.created)
           && Objects.equals(registered, that.registered)
           && Objects.equals(updated, that.updated);
  }

  @Override
  public int hashCode() {
    return Objects.hash(doi, event, identifiers, creators, titles, publisher, publicationYear, subjects, contributors, dates, language, types, relatedIdentifiers, sizes, formats, version, schemaVersion, rightsList, descriptions, geoLocations, url, contentUrl, metadataVersion, source, state, reason, created, registered, updated);
  }

  @Override
  public String toString() {
    return "DoiAttributes{" +
      "doi=" + doi +
      ", event=" + event +
      ", identifiers=" + identifiers +
      ", creators=" + creators +
      ", titles=" + titles +
      ", publisher='" + publisher + '\'' +
      ", publicationYear=" + publicationYear +
      ", subjects=" + subjects +
      ", contributors=" + contributors +
      ", dates=" + dates +
      ", language='" + language + '\'' +
      ", types=" + types +
      ", relatedIdentifiers=" + relatedIdentifiers +
      ", sizes=" + sizes +
      ", formats=" + formats +
      ", version='" + version + '\'' +
      ", schemaVersion='" + schemaVersion + '\'' +
      ", rightsList=" + rightsList +
      ", descriptions=" + descriptions +
      ", geoLocations=" + geoLocations +
      ", url='" + url + '\'' +
      ", contentUrl=" + contentUrl +
      ", metadataVersion=" + metadataVersion +
      ", source='" + source + '\'' +
      ", state=" + state +
      ", reason='" + reason + '\'' +
      ", created='" + created + '\'' +
      ", registered='" + registered + '\'' +
      ", updated='" + updated + '\'' +
      '}';
  }
}
