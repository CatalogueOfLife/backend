package life.catalogue.api.model;

import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;

import org.gbif.nameparser.api.NomCode;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DatasetWithSettings {

  private Dataset dataset;
  private DatasetSettings settings;

  public DatasetWithSettings() {
    this(new Dataset(), new DatasetSettings());
  }

  public DatasetWithSettings(Dataset dataset) {
    this(dataset, null);
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

  public boolean isEnabled(Setting key) {
    return settings.isEnabled(key);
  }

  public boolean isDisabled(Setting key) {
    return settings.isDisabled(key);
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

  @JsonIgnore
  public boolean hasDeletedDate() {
    return dataset.hasDeletedDate();
  }

  @JsonProperty("private")
  public boolean isPrivat() {
    return dataset.isPrivat();
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

  public void applyPatch(Dataset patch) {
    dataset.applyPatch(patch);
  }

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
    return dataset.getAttempt();
  }

  public void setImportAttempt(Integer importAttempt) {
    dataset.setAttempt(importAttempt);
  }

  public DOI getDoi() {
    return dataset.getDoi();
  }

  public void setDoi(DOI doi) {
    dataset.setDoi(doi);
  }

  public String getTitle() {
    return dataset.getTitle();
  }

  public void setTitle(String title) {
    dataset.setTitle(title);
  }

  public String getDescription() {
    return dataset.getDescription();
  }

  public void setDescription(String description) {
    dataset.setDescription(description);
  }

  public List<Agent> getCreator() {
    return dataset.getCreator();
  }

  public void setCreator(List<Agent> creator) {
    dataset.setCreator(creator);
  }

  public void addCreator(Agent author) {
    dataset.addCreator(author);
  }

  public List<Agent> getEditor() {
    return dataset.getEditor();
  }

  public void setEditor(List<Agent> editor) {
    dataset.setEditor(editor);
  }

  public void addEditor(Agent editor) {
    dataset.addEditor(editor);
  }

  @JsonIgnore
  public String getAliasOrTitle() {
    return dataset.getAliasOrTitle();
  }

  public void setPrivat(boolean privat) {
    dataset.setPrivat(privat);
  }

  public LocalDateTime getImported() {
    return dataset.getImported();
  }

  public void setImported(LocalDateTime imported) {
    dataset.setImported(imported);
  }

  public DatasetOrigin getOrigin() {
    return dataset.getOrigin();
  }

  public void setOrigin(DatasetOrigin origin) {
    dataset.setOrigin(origin);
  }

  public LocalDateTime getDeleted() {
    return dataset.getDeleted();
  }

  public void setDeleted(LocalDateTime deleted) {
    dataset.setDeleted(deleted);
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

  public Integer getSize() {
    return dataset.getSize();
  }

  public void setSize(Integer size) {
    dataset.setSize(size);
  }

  public String getNotes() {
    return dataset.getNotes();
  }

  public void setNotes(String notes) {
    dataset.setNotes(notes);
  }

  public void addKeyword(String keyword) {
    dataset.addKeyword(keyword);
  }

  public List<String> getKeyword() {
    return dataset.getKeyword();
  }

  public void setKeyword(List<String> keyword) {
    dataset.setKeyword(keyword);
  }

  public Map<String, String> getIdentifier() {
    return dataset.getIdentifier();
  }

  public void setIdentifier(Map<String, String> identifier) {
    dataset.setIdentifier(identifier);
  }

  public String getAlias() {
    return dataset.getAlias();
  }

  public void setAlias(String alias) {
    dataset.setAlias(alias);
  }

  public Agent getContact() {
    return dataset.getContact();
  }

  public void setContact(Agent contact) {
    dataset.setContact(contact);
  }

  public Agent getPublisher() {
    return dataset.getPublisher();
  }

  public void setPublisher(Agent publisher) {
    dataset.setPublisher(publisher);
  }

  public List<Agent> getContributor() {
    return dataset.getContributor();
  }

  public void setContributor(List<Agent> contributor) {
    dataset.setContributor(contributor);
  }

  public String getGeographicScope() {
    return dataset.getGeographicScope();
  }

  public void setGeographicScope(String geographicScope) {
    dataset.setGeographicScope(geographicScope);
  }

  public String getTaxonomicScope() {
    return dataset.getTaxonomicScope();
  }

  public void setTaxonomicScope(String taxonomicScope) {
    dataset.setTaxonomicScope(taxonomicScope);
  }

  public String getTemporalScope() {
    return dataset.getTemporalScope();
  }

  public void setTemporalScope(String temporalScope) {
    dataset.setTemporalScope(temporalScope);
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

  public FuzzyDate getIssued() {
    return dataset.getIssued();
  }

  public void setIssued(FuzzyDate issued) {
    dataset.setIssued(issued);
  }

  public URI getUrl() {
    return dataset.getUrl();
  }

  public void setUrl(URI url) {
    dataset.setUrl(url);
  }

  public URI getLogo() {
    return dataset.getLogo();
  }

  public void setLogo(URI logo) {
    dataset.setLogo(logo);
  }

  public List<Citation> getSource() {
    return dataset.getSource();
  }

  public void setSource(List<Citation> source) {
    dataset.setSource(source);
  }

  public void applyUser(User user) {
    dataset.applyUser(user);
  }

  public void applyUser(Integer userKey) {
    dataset.applyUser(userKey);
  }

  public void applyUser(Integer userKey, boolean updateCreator) {
    dataset.applyUser(userKey, updateCreator);
  }

  @Override
  public String toString() {
    return "DatasetWithSettings " + getKey() + ": " + getTitle();
  }
}
