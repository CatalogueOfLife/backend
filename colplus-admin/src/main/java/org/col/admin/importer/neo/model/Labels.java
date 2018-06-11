package org.col.admin.importer.neo.model;

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
   * Basionym with at least one HAS_BASIONYM relation
   */
  BASIONYM,

  ROOT
}
