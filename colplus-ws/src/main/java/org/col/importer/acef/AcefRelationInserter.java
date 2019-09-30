package org.col.importer.acef;

import java.util.Optional;

import org.col.importer.RelationInserterBase;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.model.NeoName;
import org.col.importer.neo.model.NeoUsage;
import org.col.api.model.NameAccordingTo;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AcefRelationInserter extends RelationInserterBase {
  private static final Logger LOG = LoggerFactory.getLogger(AcefRelationInserter.class);

  private final AcefInterpreter inter;
  
  public AcefRelationInserter(NeoDb store, AcefInterpreter inter) {
    super(store, AcefTerm.AcceptedTaxonID, AcefTerm.ParentSpeciesID);
    this.inter = inter;
  }
  
  @Override
  protected void processVerbatimUsage(NeoUsage u, VerbatimRecord v, Node p) {
    if (AcefTerm.AcceptedInfraSpecificTaxa == v.getType()) {
      // finally we have all pieces to also interpret infraspecific names
      // even with a missing parent, we will still try to build a name
      final NeoName nn = store.nameByUsage(u.node);
      Optional<NameAccordingTo> opt = Optional.empty();
      if (p != null) {
        NeoName sp = store.nameByUsage(p);
        if (sp.name.getRank() != Rank.GENUS) {
          opt = inter.interpretName(u.getId(), v.get(AcefTerm.InfraSpeciesMarker), null, v.get(AcefTerm.InfraSpeciesAuthorString),
              sp.name.getGenus(), sp.name.getInfragenericEpithet(), sp.name.getSpecificEpithet(), v.get(AcefTerm.InfraSpeciesEpithet),
              null, null,
              null, v.get(AcefTerm.GSDNameStatus), null, null, v);
        }
      }
    
      if (opt.isPresent()) {
        nn.name = opt.get().getName();
        if (!nn.name.getRank().isInfraspecific()) {
          LOG.info("Expected infraspecific taxon but found {} for name {}: {}", nn.name.getRank(), u.getId(), nn.name.getScientificName());
          v.addIssue(Issue.INCONSISTENT_NAME);
        }
      
        store.names().update(nn);
      
      } else {
        // remove name & taxon from store, only keeping the verbatim
        store.remove(nn.node);
        store.remove(u.node);
      }
    }
  }
 
}
