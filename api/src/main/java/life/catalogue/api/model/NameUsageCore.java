package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

public interface NameUsageCore extends HasID<String> {

  String getParentId();
  Rank getRank();
  TaxonomicStatus getStatus();
}
