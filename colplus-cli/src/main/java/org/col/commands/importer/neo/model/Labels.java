package org.col.commands.importer.neo.model;

import org.neo4j.graphdb.Label;

/**
 *
 */
public enum Labels implements Label {
  TAXON,
  SYNONYM,

  ROOT,
  AUTONYM,
  IMPLICIT
}
