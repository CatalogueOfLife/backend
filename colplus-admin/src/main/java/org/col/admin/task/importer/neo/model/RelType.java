package org.col.admin.task.importer.neo.model;

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
   * with optional property "homotypic" of any non null value
   * to indicate a homotypic synonym which results in sharing the same homotypic group key at the end
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
