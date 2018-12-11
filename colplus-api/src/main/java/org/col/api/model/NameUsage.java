package org.col.api.model;

import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public interface NameUsage extends ID, VerbatimEntity {

  Name getName();
  
  void setName(Name name);

  TaxonomicStatus getStatus();
  
  void setStatus(TaxonomicStatus status);

  String getAccordingTo();
  
  void setAccordingTo(String according);
  
  default boolean isSynonym() {
    return getStatus() != null && getStatus().isSynonym();
  }
  
  default boolean isTaxon() {
    return getStatus() != null && !getStatus().isSynonym();
  }
  
  default boolean isBareName() {
    return getStatus() == null;
  }
}
