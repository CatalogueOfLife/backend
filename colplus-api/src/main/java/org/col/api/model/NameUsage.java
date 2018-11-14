package org.col.api.model;

import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public interface NameUsage extends VerbatimEntity {

  Name getName();
  
  void setName(Name name);

  TaxonomicStatus getStatus();
  
  void setStatus(TaxonomicStatus status);

  String getAccordingTo();
  
  void setAccordingTo(String according);
}
