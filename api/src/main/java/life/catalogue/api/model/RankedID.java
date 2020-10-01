package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

public interface RankedID {

  String getId();
  Rank getRank();
}
