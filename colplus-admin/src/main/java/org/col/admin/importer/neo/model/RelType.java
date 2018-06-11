package org.col.admin.importer.neo.model;

import org.col.api.vocab.NomRelType;
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
  HAS_BASIONYM("bas", NomRelType.BASIONYM),

  SPELLING_CORRECTION_OF("", NomRelType.SPELLING_CORRECTION),

  BASED_ON("", NomRelType.BASED_ON),

  REPLACEMENT_NAME_OF("", NomRelType.REPLACEMENT_NAME),

  CONSERVED_AGAINST("", NomRelType.CONSERVED),

  LATER_HOMONYM_OF("", NomRelType.LATER_HOMONYM),

  SUPERFLUOUS_BECAUSE_OF("", NomRelType.SUPERFLUOUS);

  public final NomRelType nomRelType;
  public final String abbrev;

  RelType(String abbrev) {
    this(abbrev,null);
  }

  RelType(String abbrev, NomRelType type) {
    this.abbrev = abbrev;
    this.nomRelType = type;
  }
}
