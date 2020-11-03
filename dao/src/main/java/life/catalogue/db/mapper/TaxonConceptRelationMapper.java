package life.catalogue.db.mapper;

import life.catalogue.api.model.TaxonConceptRelation;
import life.catalogue.api.vocab.TaxonConceptRelType;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaxonConceptRelationMapper extends Create<TaxonConceptRelation>,
                                                    DatasetProcessable<TaxonConceptRelation>,
                                                    SectorProcessable<TaxonConceptRelation>,
                                                    CopyDataset {
  
  /**
   * Returns the list of taxon concept relations for a single taxon,
   * regardless which side of the relation the taxon is on.
   */
  List<TaxonConceptRelation> list(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);
  
  /**
   * Returns the list of related taxon concepts of a given type for a single taxon on the taxonId side of the relation only.
   */
  List<TaxonConceptRelation> listByType(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId, @Param("type") TaxonConceptRelType type);
  
}
