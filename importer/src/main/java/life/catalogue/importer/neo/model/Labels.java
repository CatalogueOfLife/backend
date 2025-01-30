package life.catalogue.importer.neo.model;

import org.neo4j.graphdb.Label;

/**
 * Neo4j labels we use in queries
 */
public enum Labels implements Label {

  /**
   * Applied to nodes that contain a name
   */
  NAME,
  
  /**
   * Additional super label for Taxon or Synonym
   */
  USAGE,

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
  
  ROOT,

  /**
   * A dummy node that should not exist in postgres, but is needed to create some neo relationships.
   * E.g. for species interactions with a missing relatedTaxonID.
   */
  DEV_NULL
}
