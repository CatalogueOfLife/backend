package org.col.commands.importer.neo.model;

import org.neo4j.graphdb.Label;

/**
 *
 */
public enum Labels implements Label {
  /**
   * Applied to all nodes, i.e. taxon or synonym
   */
  ALL,

  /**
   * Accepted taxa only, subclass of ALL
   */
  TAXON,

  /**
   * Synonyms only, subclass of ALL
   */
  SYNONYM,

  ROOT,
  AUTONYM,
  IMPLICIT
}
