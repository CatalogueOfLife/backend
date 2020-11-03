package life.catalogue.db.mapper;

import life.catalogue.api.model.SpeciesInteraction;
import life.catalogue.api.vocab.SpeciesInteractionType;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SpeciesInteractionMapper extends Create<SpeciesInteraction>,
                                                    DatasetProcessable<SpeciesInteraction>,
                                                    SectorProcessable<SpeciesInteraction>,
                                                    CopyDataset {
  
  /**
   * Returns the list of species interactions for a single taxon,
   * regardless which side of the relation the taxon is on.
   */
  List<SpeciesInteraction> list(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);
  
  /**
   * Returns the list of species interactions of a given type for a single taxon on the taxonId side of the relation only.
   */
  List<SpeciesInteraction> listByType(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId, @Param("type") SpeciesInteractionType type);
  
}
