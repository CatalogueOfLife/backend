package life.catalogue.api.model;

import life.catalogue.api.vocab.License;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * The pure descriptive metadata of a dataset, excluding all internal aspects, timestamps and settings.
 */
public interface DatasetMetadata {

  Integer getKey();

  void setKey(Integer key);

  String getTitle();

  void setTitle(String title);

  String getAlias();

  void setAlias(String alias);

  String getDescription();

  void setDescription(String description);

  List<Person> getAuthorsAndEditors();

  void setAuthorsAndEditors(List<Person> authorsAndEditors);

  List<String> getOrganisations();

  void setOrganisations(List<String> organisations);

  Person getContact();

  void setContact(Person contact);

  License getLicense();

  void setLicense(License license);

  String getVersion();

  void setVersion(String version);

  LocalDate getReleased();

  void setReleased(LocalDate released);

  String getGeographicScope();

  void setGeographicScope(String geographicScope);

  String getCitation();

  void setCitation(String citation);

  URI getWebsite();

  void setWebsite(URI website);

  String getGroup();

  void setGroup(String group);

  Integer getConfidence();

  void setConfidence(Integer confidence);

  Integer getCompleteness();

  void setCompleteness(Integer completeness);
}
