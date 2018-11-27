package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Common human metadata characterizing a data source.
 */
public interface SourceMetadata {
  
  String getTitle();
  
  void setTitle(String title);
  
  String getDescription();
  
  void setDescription(String description);
  
  List<String> getAuthorsAndEditors();
  
  void setAuthorsAndEditors(List<String> authorsAndEditors);
  
  
  String getContactPerson();
  
  void setContactPerson(String contactPerson);
  
  String getVersion();
  
  void setVersion(String version);
  
  /**
   * Release date of the source data.
   * The date can usually only be taken from metadata explicitly given by the source.
   */
  LocalDate getReleased();
  
  void setReleased(LocalDate released);
  
  URI getHomepage();
  
  void setHomepage(URI homepage);
  
  String getCitation();
  
  void setCitation(String citation);
}
