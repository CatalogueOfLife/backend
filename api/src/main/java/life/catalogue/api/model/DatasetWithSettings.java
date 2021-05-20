package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.vocab.*;
import org.gbif.nameparser.api.NomCode;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class DatasetWithSettings {

  private Dataset dataset;
  private DatasetSettings settings;

  public DatasetWithSettings() {
    this(new Dataset(), new DatasetSettings());
  }

  public DatasetWithSettings(Dataset dataset, DatasetSettings settings) {
    this.dataset = dataset;
    this.settings = settings;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public void setDataset(Dataset dataset) {
    this.dataset = dataset;
  }

  public DatasetSettings getSettings() {
    return settings;
  }

  public void setSettings(DatasetSettings settings) {
    this.settings = settings;
  }

  public void putSettings(DatasetSettings settings) {
    this.settings.putAll(settings);
  }

  public String getString(Setting key) {
    return settings.getString(key);
  }

  public Boolean getBool(Setting key) {
    return settings.getBool(key);
  }

  public Integer getInt(Setting key) {
    return settings.getInt(key);
  }

  public URI getURI(Setting key) {
    return settings.getURI(key);
  }

  public <T extends Enum> T getEnum(Setting key) {
    return settings.getEnum(key);
  }

  public Object put(Setting key, Object value) {
    return settings.put(key, value);
  }

  public boolean has(Setting key) {
    return settings.has(key);
  }

  public Gazetteer getGazetteer() {
    return settings.getEnum(Setting.DISTRIBUTION_GAZETTEER);
  }

  public void setGazetteer(Gazetteer gazetteer){
    settings.put(Setting.DISTRIBUTION_GAZETTEER, gazetteer);
  }

  public NomCode getCode() {
    return settings.getEnum(Setting.NOMENCLATURAL_CODE);
  }

  public void setCode(NomCode code){
    settings.put(Setting.NOMENCLATURAL_CODE, code);
  }

  public DataFormat getDataFormat() {
    return settings.getEnum(Setting.DATA_FORMAT);
  }

  public void setDataFormat(DataFormat format){
    settings.put(Setting.DATA_FORMAT, format);
  }

  public URI getDataAccess() {
    return settings.getURI(Setting.DATA_ACCESS);
  }

  public void setDataAccess(URI dataAccess) {
    settings.put(Setting.DATA_ACCESS, dataAccess);
  }

  // *** DATASET ***

  public Integer getKey() {
    return dataset.getKey();
  }

  public DatasetType getType() {
    return dataset.getType();
  }

  public void setType(DatasetType type) {
    dataset.setType(type);
  }

  public void setKey(Integer key) {
    dataset.setKey(key);
  }

  public Integer getSourceKey() {
    return dataset.getSourceKey();
  }

  public void setSourceKey(Integer sourceKey) {
    dataset.setSourceKey(sourceKey);
  }

  public Integer getImportAttempt() {
    return dataset.getImportAttempt();
  }

  public void setImportAttempt(Integer importAttempt) {
    dataset.setImportAttempt(importAttempt);
  }

  public DOI getDoi() {
    return dataset.getDoi();
  }

  public void setDoi(DOI doi) {
    dataset.setDoi(doi);
  }

  public LocalDateTime getDeleted() {
    return dataset.getDeleted();
  }

  @JsonIgnore
  public boolean hasDeletedDate() {
    return dataset.hasDeletedDate();
  }

  public void setDeleted(LocalDateTime deleted) {
    dataset.setDeleted(deleted);
  }

  public String getTitle() {
    return dataset.getTitle();
  }

  public void setTitle(String title) {
    dataset.setTitle(title);
  }

  public UUID getGbifKey() {
    return dataset.getGbifKey();
  }

  public void setGbifKey(UUID gbifKey) {
    dataset.setGbifKey(gbifKey);
  }

  public UUID getGbifPublisherKey() {
    return dataset.getGbifPublisherKey();
  }

  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    dataset.setGbifPublisherKey(gbifPublisherKey);
  }

  public String getDescription() {
    return dataset.getDescription();
  }

  public void setDescription(String description) {
    dataset.setDescription(description);
  }

  public List<Person> getAuthors() {
    return dataset.getAuthors();
  }

  public void setAuthors(List<Person> authors) {
    dataset.setAuthors(authors);
  }

  public List<Person> getEditors() {
    return dataset.getEditors();
  }

  public void setEditors(List<Person> editors) {
    dataset.setEditors(editors);
  }

  public List<Organisation> getOrganisations() {
    return dataset.getOrganisations();
  }

  public void setOrganisations(List<Organisation> organisations) {
    dataset.setOrganisations(organisations);
  }

  public Person getContact() {
    return dataset.getContact();
  }

  public void setContact(Person contact) {
    dataset.setContact(contact);
  }

  public License getLicense() {
    return dataset.getLicense();
  }

  public void setLicense(License license) {
    dataset.setLicense(license);
  }

  public String getVersion() {
    return dataset.getVersion();
  }

  public void setVersion(String version) {
    dataset.setVersion(version);
  }

  public String getGeographicScope() {
    return dataset.getGeographicScope();
  }

  public void setGeographicScope(String geographicScope) {
    dataset.setGeographicScope(geographicScope);
  }

  public LocalDate getReleased() {
    return dataset.getReleased();
  }

  public void setReleased(LocalDate released) {
    dataset.setReleased(released);
  }

  public String getCitation() {
    return dataset.getCitation();
  }

  public void setCitation(String citation) {
    dataset.setCitation(citation);
  }

  public URI getWebsite() {
    return dataset.getWebsite();
  }

  public void setWebsite(URI website) {
    dataset.setWebsite(website);
  }

  public URI getLogo() {
    return dataset.getLogo();
  }

  public void setLogo(URI logo) {
    dataset.setLogo(logo);
  }

  public String getNotes() {
    return dataset.getNotes();
  }

  public void setNotes(String notes) {
    dataset.setNotes(notes);
  }

  public String getAlias() {
    return dataset.getAlias();
  }

  public void setAlias(String alias) {
    dataset.setAlias(alias);
  }

  public String getGroup() {
    return dataset.getGroup();
  }

  public void setGroup(String group) {
    dataset.setGroup(group);
  }

  public Integer getConfidence() {
    return dataset.getConfidence();
  }

  public void setConfidence(Integer confidence) {
    dataset.setConfidence(confidence);
  }

  public Integer getCompleteness() {
    return dataset.getCompleteness();
  }

  public void setCompleteness(Integer completeness) {
    dataset.setCompleteness(completeness);
  }

  public LocalDateTime getCreated() {
    return dataset.getCreated();
  }

  public void setCreated(LocalDateTime created) {
    dataset.setCreated(created);
  }

  public Integer getCreatedBy() {
    return dataset.getCreatedBy();
  }

  public void setCreatedBy(Integer createdBy) {
    dataset.setCreatedBy(createdBy);
  }

  public LocalDateTime getModified() {
    return dataset.getModified();
  }

  public void setModified(LocalDateTime modified) {
    dataset.setModified(modified);
  }

  public Integer getModifiedBy() {
    return dataset.getModifiedBy();
  }

  public void setModifiedBy(Integer modifiedBy) {
    dataset.setModifiedBy(modifiedBy);
  }

  public DatasetOrigin getOrigin() {
    return dataset.getOrigin();
  }

  public void setOrigin(DatasetOrigin origin) {
    dataset.setOrigin(origin);
  }

  @JsonProperty("private")
  public boolean isPrivat() {
    return dataset.isPrivat();
  }

  public void setPrivat(boolean privat) {
    dataset.setPrivat(privat);
  }

  @Override
  public String toString() {
    return "DatasetWithSettings " + getKey() + ": " + getTitle();
  }
}
