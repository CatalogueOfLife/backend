package org.col.admin.importer.acef;

import java.util.Optional;

import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.api.model.NameAccordingTo;
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
public class AcefRelationInserter implements NodeBatchProcessor {
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
        VerbatimRecord v;
        if (t.isSynonym()) {
          v = store.getVerbatim(t.synonym.getVerbatimKey());
          Node an = lookupByID(AcefTerm.AcceptedTaxonID, v, t);
          if (an != null) {
            store.createSynonymRel(t.node, an);
          } else {
            // if we aint got no idea of the accepted insert just the name
            v.addIssue(Issue.ACCEPTED_ID_INVALID);
            v.addIssue(Issue.ACCEPTED_NAME_MISSING);
            store.update(t);
          }

        } else {
          v = store.getVerbatim(t.taxon.getVerbatimKey());
          Node p = lookupByID(AcefTerm.ParentSpeciesID, v, t);
          if (p != null) {
            store.assignParent(p, t.node);
          } else {
            v.addIssue(Issue.PARENT_ID_INVALID);
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
                null, v.get(AcefTerm.GSDNameStatus), null, null, v);

            if (opt.isPresent()) {
              t.name = opt.get().getName();
              if (!t.name.getRank().isInfraspecific()) {
                LOG.info("Expected infraspecific taxon but found {} for name {}: {}", t.name.getRank(), t.getID(), t.name.getScientificName());
                v.addIssue(Issue.INCONSISTENT_NAME);
              }
              store.put(t);

            } else {
              // remove name & taxon from store, only keeping the verbatim
              store.remove(n);
            }
          }
        }
        store.put(v);
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
  private Node lookupByID(Term term, VerbatimRecord v, NeoTaxon t) {
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
