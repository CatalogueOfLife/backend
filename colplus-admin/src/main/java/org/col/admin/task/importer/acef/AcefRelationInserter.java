package org.col.admin.task.importer.acef;

import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AcefRelationInserter implements NeoDb.NodeBatchProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(AcefRelationInserter.class);

  private final NeoDb store;
  private final InsertMetadata meta;

  public AcefRelationInserter(NeoDb store, InsertMetadata meta) {
    this.store = store;
    this.meta = meta;
  }

  @Override
  public void process(Node n) {
    try {
      NeoTaxon t = store.get(n);
      if (t.synonym != null) {
        Node acc = lookupByTaxonID(AcefTerm.AcceptedTaxonID, t, Issue.ACCEPTED_ID_INVALID);
        if (acc != null) {
          store.createSynonymRel(t.node, acc);
        }

      } else {
        Node p = lookupByTaxonID(AcefTerm.ParentSpeciesID, t, Issue.PARENT_ID_INVALID);
        if (p != null) {
          store.assignParent(p, t.node);
          // update infraspecific name with species
          NeoTaxon sp = store.get(p);
          t.name.setGenus(sp.name.getGenus());
          t.name.setInfragenericEpithet(sp.name.getInfragenericEpithet());
          t.name.setSpecificEpithet(sp.name.getSpecificEpithet());
        }
      }
      if (t.name.getScientificName() == null) {
        // this should be an infraspecific name not yet updated in AcefInserter, do it here!
        AcefInterpreter.updateScientificName(t.name);
      }
      store.put(t);

    } catch (Exception e) {
      LOG.error("error processing explicit relations for {} {}", n, NeoProperties.getScientificNameWithAuthor(n), e);
    }
  }

  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * @return list of potentially split ids with their matching neo node if found, otherwise null
   */
  private Node lookupByTaxonID(Term term, NeoTaxon t, Issue issueIfNotFound) {
    Node n = null;
    final String id = t.verbatim.getCoreTerm(term);
    if (id != null && !id.equals(t.getTaxonID())) {
      n = store.byTaxonID(id);
      if (n == null) {
        t.addIssue(issueIfNotFound);
      }
    }
    return n;
  }



  @Override
  public void commitBatch(int counter) {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit {} early with incomplete parsing", store.getDataset());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
    LOG.debug("Processed relations for {} nodes", counter);
  }
}
