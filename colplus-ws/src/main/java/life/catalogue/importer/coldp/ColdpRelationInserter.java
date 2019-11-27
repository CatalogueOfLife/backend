package life.catalogue.importer.coldp;

import life.catalogue.importer.RelationInserterBase;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.api.datapackage.ColdpTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ColdpRelationInserter extends RelationInserterBase {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpRelationInserter.class);

  private final ColdpInterpreter inter;

  ColdpRelationInserter(NeoDb store, ColdpInterpreter inter) {
    super(store, ColdpTerm.taxonID, ColdpTerm.parentID);
    this.inter = inter;
  }

}
