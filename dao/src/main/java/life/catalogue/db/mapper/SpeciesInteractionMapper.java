package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SpeciesInteraction;
import life.catalogue.api.vocab.SpeciesInteractionType;
import life.catalogue.db.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SpeciesInteractionMapper extends Create<SpeciesInteraction>,
                                                    DatasetProcessable<SpeciesInteraction>,
                                                    SectorProcessable<SpeciesInteraction>,
                                                    TaxonProcessable<SpeciesInteraction>,
                                                    CopyDataset {
  /**
   * Returns the list of species interactions for a single taxon on the related side of the relation.
   */
  List<SpeciesInteraction> listByRelatedTaxon(@Param("key") DSID<String> key);

  /**
   * Returns the list of species interactions of a given type for a single taxon on the taxonId side of the relation only.
   */
  List<SpeciesInteraction> listByType(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId, @Param("type") SpeciesInteractionType type);
  
}
