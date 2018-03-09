package org.col.admin.task.importer.acef;

import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.model.VerbatimRecord;
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
  private final AcefInterpreter inter;

  public AcefRelationInserter(NeoDb store, AcefInterpreter inter) {
    this.store = store;
    this.inter = inter;
  }

  @Override
  public void process(Node n) {
    try {
      NeoTaxon t = store.get(n);
      if (t.synonym != null) {
        Node acc = lookupByID(AcefTerm.AcceptedTaxonID, t, Issue.ACCEPTED_ID_INVALID);
        if (acc != null) {
          store.createSynonymRel(t.node, acc);
        }

      } else {
        Node p = lookupByID(AcefTerm.ParentSpeciesID, t, Issue.PARENT_ID_INVALID);
        if (p != null) {
          store.assignParent(p, t.node);
          // finally we have all pieces to also interpret infraspecific names
          NeoTaxon sp = store.get(p);
          VerbatimRecord v = t.verbatim;
          t.name = inter.interpretName(v.getId(), v.getTerm(AcefTerm.InfraSpeciesMarker), null, v.getTerm(AcefTerm.InfraSpeciesAuthorString),
              sp.name.getGenus(), sp.name.getInfragenericEpithet(), sp.name.getSpecificEpithet(), v.getTerm(AcefTerm.InfraSpeciesEpithet),
              null, v.getTerm(AcefTerm.GSDNameStatus), null, null);
          if (!t.name.getRank().isInfraspecific()) {
            LOG.info("Expected infraspecific taxon but found {} for name {}: {}", t.name.getRank(), v.getId(), t.name.getScientificName());
            t.addIssue(Issue.INCONSISTENT_NAME);
          }
        }
      }
      // interpret distributions
      inter.interpretDistributions(t);
      // interpret vernaculars
      inter.interpretVernaculars(t);

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
  private Node lookupByID(Term term, NeoTaxon t, Issue issueIfNotFound) {
    Node n = null;
    final String id = t.verbatim.getTerm(term);
    if (id != null && !id.equals(t.getID())) {
      n = store.byID(id);
      if (n == null) {
        t.addIssue(issueIfNotFound);
      }
    }
    return n;
  }



  @Override
  public void commitBatch(int counter) {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit dataset {} early with incomplete parsing", store.getDataset().getKey());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
    LOG.debug("Processed relations for {} nodes", counter);
  }
}
