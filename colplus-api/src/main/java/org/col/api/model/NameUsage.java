package org.col.api.model;

import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public interface NameUsage {

  Name getName();

  TaxonomicStatus getStatus();

  String getAccordingTo();
}
