package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DSID;
import org.col.api.model.Synonym;
import org.col.db.CRUD;
import org.col.db.DatasetPageable;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, TaxonVernacularUsage,
 * Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface SynonymMapper extends CRUD<DSID<String>, Synonym>, ProcessableDataset<Synonym>, DatasetPageable<Synonym> {
  
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
