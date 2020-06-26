package life.catalogue.importer.coldp;

import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.importer.RelationInserterBase;
import life.catalogue.importer.neo.NeoDb;

/**
 *
 */
public class ColdpRelationInserter extends RelationInserterBase {

  ColdpRelationInserter(NeoDb store) {
    super(store, ColdpTerm.taxonID, ColdpTerm.parentID, ColdpTerm.originalNameID);
  }

}
