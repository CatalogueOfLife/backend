package org.col.admin.importer.acef;

import java.util.Optional;

import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.NeoUsage;
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
      NeoUsage u = store.usages().objByNode(n);
      if (u.getVerbatimKey() != null) {
        VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
        if (u.isSynonym()) {
          Node an = lookupByID(AcefTerm.AcceptedTaxonID, v, u);
          if (an != null) {
            store.createSynonymRel(u.node, an);
          } else {
            // if we aint got no idea of the accepted insert just the name
            v.addIssues(Issue.ACCEPTED_ID_INVALID, Issue.ACCEPTED_NAME_MISSING);
          }

        } else {
          Node p = lookupByID(AcefTerm.ParentSpeciesID, v, u);
          if (p != null) {
            store.assignParent(p, u.node);
          } else {
            v.addIssue(Issue.PARENT_ID_INVALID);
          }

          if (AcefTerm.AcceptedInfraSpecificTaxa == v.getType()) {
            // finally we have all pieces to also interpret infraspecific names
            // even with a missing parent, we will still try to build a name
            final NeoName nn = store.names().objByNode(n);
            String genus = null;
            String infragenericEpithet = null;
            String specificEpithet = null;
            if (p != null) {
              NeoName sp = store.names().objByNode(p);
              genus = sp.name.getGenus();
              infragenericEpithet = sp.name.getInfragenericEpithet();
              specificEpithet = sp.name.getSpecificEpithet();
            }
            Optional<NameAccordingTo> opt = inter.interpretName(u.getId(), v.get(AcefTerm.InfraSpeciesMarker), null, v.get(AcefTerm.InfraSpeciesAuthorString),
                genus, infragenericEpithet, specificEpithet, v.get(AcefTerm.InfraSpeciesEpithet),
                null, v.get(AcefTerm.GSDNameStatus), null, null, v);

            if (opt.isPresent()) {
              nn.name = opt.get().getName();
              if (!nn.name.getRank().isInfraspecific()) {
                LOG.info("Expected infraspecific taxon but found {} for name {}: {}", nn.name.getRank(), u.getId(), nn.name.getScientificName());
                v.addIssue(Issue.INCONSISTENT_NAME);
              }
              
              store.names().update(nn);

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
  private Node lookupByID(Term term, VerbatimRecord v, NeoUsage t) {
    Node n = null;
    final String id = v.get(term);
    if (id != null && !id.equals(t.getId())) {
      n = store.usages().nodeByID(id);
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
