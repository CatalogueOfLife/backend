package org.col.api;

import org.col.api.model.Name;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

/**
 *
 */
public interface NamesIndexService {

  Integer lookup(Name name, boolean insertIfMissing);

  Integer lookup(NomCode code, Rank rank, String scientificName);
}
