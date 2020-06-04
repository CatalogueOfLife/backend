package life.catalogue.api.model;

import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

/**
 *
 */
public interface NameUsage extends DSID<String>, VerbatimEntity {

  String getLabel();

  String getLabelHtml();

  Name getName();
  
  void setName(Name name);

  TaxonomicStatus getStatus();
  
  void setStatus(TaxonomicStatus status);
  
  Origin getOrigin();
  
  void setOrigin(Origin origin);
  
  String getAccordingToId();
  
  void setAccordingToId(String according);
  
  default boolean isSynonym() {
    return getStatus() != null && getStatus().isSynonym();
  }
  
  default boolean isTaxon() {
    return getStatus() != null && !getStatus().isSynonym();
  }
  
  default boolean isBareName() {
    return getStatus() == null;
  }

  String getRemarks();
  
  void setRemarks(String remarks);
}
