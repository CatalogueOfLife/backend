package org.col.admin.importer.coldp;

import org.col.admin.importer.RelationInserterBase;
import org.col.admin.importer.neo.NeoDb;
import org.col.api.datapackage.ColTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ColdpRelationInserter extends RelationInserterBase {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpRelationInserter.class);

  private final ColdpInterpreter inter;

  ColdpRelationInserter(NeoDb store, ColdpInterpreter inter) {
    super(store, ColTerm.taxonID, ColTerm.parentID);
    this.inter = inter;
  }

}
