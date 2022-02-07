package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Synonym;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Mapper dealing only with Synonym usages.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface SynonymMapper extends CRUD<DSID<String>, Synonym>, DatasetProcessable<Synonym>, DatasetPageable<Synonym> {
  
  /**
   * Return synonyms including misapplied names from the synonym relation table.
   * The Synonym.accepted property is NOT set as it would be highly redundant with the accepted key being the parameter.
   * <p>
   * We use this call to assemble a complete synonymy
   * and the accepted key is given as the parameter already
   *
   * @param taxonId accepted taxon id
   * @return list of misapplied or heterotypic synonym names ordered by status then homotypic group
   */
  List<Synonym> listByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);
  
  List<Synonym> listByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  
}
