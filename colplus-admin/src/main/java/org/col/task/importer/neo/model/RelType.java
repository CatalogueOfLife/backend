package org.col.task.importer.neo.model;

import org.neo4j.graphdb.RelationshipType;

/**
 *
 */
public enum RelType implements RelationshipType {
  /**
   * Taxon -> Taxon
   */
  PARENT_OF("par"),

  /**
   * Name -> Taxon
   */
  SYNONYM_OF("syn"),

  /**
   * Name -> Name
   */
  BASIONYM_OF("bas");

  public final String abbrev;

  RelType(String abbrev) {
    this.abbrev = abbrev;
  }
}
