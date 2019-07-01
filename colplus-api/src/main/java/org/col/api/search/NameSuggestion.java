package org.col.api.search;

import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

public class NameSuggestion {

  private String name;
  private Rank rank;
  private TaxonomicStatus status;
  private NomCode code;

}
