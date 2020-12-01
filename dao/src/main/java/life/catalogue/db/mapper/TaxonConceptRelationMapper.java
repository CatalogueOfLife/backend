package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TaxonConceptRelation;
import life.catalogue.api.vocab.TaxonConceptRelType;
import life.catalogue.db.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaxonConceptRelationMapper extends Create<TaxonConceptRelation>,
                                                    DatasetProcessable<TaxonConceptRelation>,
                                                    SectorProcessable<TaxonConceptRelation>,
                                                    TaxonProcessable<TaxonConceptRelation>,
                                                    CopyDataset {
  
  /**
   * Returns the list of taxon concept relations for a single taxon on the related side of the relation.
   */
  List<TaxonConceptRelation> listByRelatedTaxon(@Param("key") DSID<String> key);

  /**
   * Returns the list of related taxon concepts of a given type for a single taxon on the taxonId side of the relation only.
   */
  List<TaxonConceptRelation> listByType(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId, @Param("type") TaxonConceptRelType type);
  
}
