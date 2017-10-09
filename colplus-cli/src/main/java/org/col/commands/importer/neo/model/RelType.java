package org.col.commands.importer.neo.model;

import org.neo4j.graphdb.RelationshipType;

/**
 *
 */
public enum RelType implements RelationshipType {
  /**
   * Taxon -> Taxon
   */
  PARENT_OF,

  /**
   * Name -> Taxon
   */
  SYNONYM_OF,

  /**
   * Name -> Name
   */
  BASIONYM_OF;

}
