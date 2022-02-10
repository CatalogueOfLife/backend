package life.catalogue.importer.coldp;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.importer.RelationInserterBase;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoUsage;

import org.neo4j.graphdb.Node;

/**
 *
 */
public class ColdpRelationInserter extends RelationInserterBase {

  ColdpRelationInserter(NeoDb store) {
    super(store, ColdpTerm.taxonID, ColdpTerm.parentID, ColdpTerm.basionymID);
  }

  @Override
  protected Node processUsage(NeoUsage u, VerbatimRecord v) {
    // NameUsage.parentID is also used for accepted taxa in synonyms
    if (v.getType() == ColdpTerm.NameUsage) {
      Node p = usageByID(ColdpTerm.parentID, v, u);
      if (u.isSynonym()) {
        if (p != null) {
          if (!store.createSynonymRel(u.node, p)) {
            v.addIssue(Issue.ACCEPTED_ID_INVALID);
          }
        } else {
          // if we ain't got no idea of the accepted flag it
          // the orphan synonym usage will be removed later by the normalizer
          v.addIssues(Issue.ACCEPTED_NAME_MISSING);
        }

      } else {
        if (p != null && !p.equals(u.node)) {
          store.assignParent(p, u.node);
        }
      }
      return p;

    } else {
      return super.processUsage(u, v);
    }
  }

}
