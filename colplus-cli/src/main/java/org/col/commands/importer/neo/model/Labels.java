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
   * Accepted taxa only
   */
  TAXON,

  /**
   * Synonyms only
   */
  SYNONYM,

  /**
   * Proparte synonyms which have at least 2 SYNONYM_OF relations, subclass of SYNONYM
   */
  PROPARTE_SYNONYM,

  ROOT
}
