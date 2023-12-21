package life.catalogue.importer.acef;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.importer.NameInterpreter;
import life.catalogue.importer.RelationInserterBase;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.Rank;

import java.util.Optional;

import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AcefRelationInserter extends RelationInserterBase {
  private static final Logger LOG = LoggerFactory.getLogger(AcefRelationInserter.class);

  private final AcefInterpreter inter;
  private final NameInterpreter nameInterpreter;
  private final DatasetSettings settings;

  public AcefRelationInserter(NeoDb store, AcefInterpreter inter) {
    super(store, AcefTerm.AcceptedTaxonID, AcefTerm.ParentSpeciesID, null);
    this.inter = inter;
    this.nameInterpreter = new NameInterpreter(inter.getSettings(), true);
    this.settings = inter.getSettings();
  }
  
  @Override
  protected void processVerbatimUsage(NeoUsage u, VerbatimRecord v, Node p) throws InterruptedException {
    if (AcefTerm.AcceptedInfraSpecificTaxa == v.getType()) {
      // finally we have all pieces to also interpret infraspecific names
      // even with a missing parent, we will still try to build a name
      final NeoName nn = store.nameByUsage(u.node);
      Optional<ParsedNameUsage> opt = Optional.empty();
      if (p != null) {
        NeoName sp = store.nameByUsage(p);
        if (sp.getName().getRank() != Rank.GENUS) {
          opt = nameInterpreter.interpret(
            u.getId(), v.get(AcefTerm.InfraSpeciesMarker), null, null, v.get(AcefTerm.InfraSpeciesAuthorString),null,null,
            sp.getName().getGenus(), sp.getName().getInfragenericEpithet(), sp.getName().getSpecificEpithet(), v.get(AcefTerm.InfraSpeciesEpithet), null,
            null,null,null,null,null,null,
            null, null,null, AcefTerm.GSDNameStatus,
            null, null, null, v
          );
        }
      }
    
      if (opt.isPresent()) {
        nn.pnu = opt.get();
        if (!nn.getName().getRank().isInfraspecific()) {
          LOG.info("Expected infraspecific taxon but found {} for name {}: {}", nn.getName().getRank(), u.getId(), nn.getName().getScientificName());
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
