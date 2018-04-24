package org.col.admin.task.importer.neo.model;

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
   * Basionym with at least one BASIONYM_OF relation
   */
  BASIONYM,

  ROOT
}
