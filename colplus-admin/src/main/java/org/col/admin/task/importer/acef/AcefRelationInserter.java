package org.col.admin.task.importer.acef;

import java.util.Optional;

import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.RankedName;
import org.col.api.model.NameAccordingTo;
import org.col.api.model.TermRecord;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
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
      if (t.taxon.getVerbatimKey() != null) {
        TermRecord v = store.getVerbatim(t.taxon.getVerbatimKey());
        if (t.synonym != null) {
          Node an = lookupByID(AcefTerm.AcceptedTaxonID, v, t);
          if (an != null) {
            store.createSynonymRel(t.node, an);
          } else {
            t.taxon.addIssue(Issue.ACCEPTED_ID_INVALID);
            t.taxon.addIssue(Issue.ACCEPTED_NAME_MISSING);
            // if we aint got no idea of the accepted insert an incertae sedis record of same rank
            NeoDb.PLACEHOLDER.setRank(t.name.getRank());
            NeoTaxon acc = NeoTaxon.createTaxon(Origin.MISSING_ACCEPTED, NeoDb.PLACEHOLDER, true);
            store.put(acc);
            store.createSynonymRel(t.node, acc.node);
            store.update(t);
          }

        } else {
          Node p = lookupByID(AcefTerm.ParentSpeciesID, v, t);
          if (p != null) {
            store.assignParent(p, t.node);
          } else {
            t.taxon.addIssue(Issue.PARENT_ID_INVALID);
            store.update(t);
          }

          if (AcefTerm.AcceptedInfraSpecificTaxa == v.getType()) {
            // finally we have all pieces to also interpret infraspecific names
            // even with a missing parent, we will still try to build a name
            String genus = null;
            String infragenericEpithet = null;
            String specificEpithet = null;
            if (p != null) {
              NeoTaxon sp = store.get(p);
              genus = sp.name.getGenus();
              infragenericEpithet = sp.name.getInfragenericEpithet();
              specificEpithet = sp.name.getSpecificEpithet();
            }
            Optional<NameAccordingTo> opt = inter.interpretName(t.getID(), v.get(AcefTerm.InfraSpeciesMarker), null, v.get(AcefTerm.InfraSpeciesAuthorString),
                genus, infragenericEpithet, specificEpithet, v.get(AcefTerm.InfraSpeciesEpithet),
                null, v.get(AcefTerm.GSDNameStatus), null, null);

            if (opt.isPresent()) {
              t.name = opt.get().getName();
              if (!t.name.getRank().isInfraspecific()) {
                LOG.info("Expected infraspecific taxon but found {} for name {}: {}", t.name.getRank(), t.getID(), t.name.getScientificName());
                t.name.addIssue(Issue.INCONSISTENT_NAME);
              }
              store.put(t);

            } else {
              // remove name & taxon from store, only keeping the verbatim
              store.remove(n);
            }
          }
        }
      }

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
  private Node lookupByID(Term term, TermRecord v, NeoTaxon t) {
    Node n = null;
    final String id = v.get(term);
    if (id != null && !id.equals(t.getID())) {
      n = store.byID(id);
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
