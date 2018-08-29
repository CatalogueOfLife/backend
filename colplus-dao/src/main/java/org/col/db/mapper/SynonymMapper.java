package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Synonym;

/**
 *
 */
public interface SynonymMapper {

  /**
   * Creates a new synonym link for a given name to the accepted taxon.
   */
  void create(@Param("datasetKey") int datasetKey,
							@Param("nameKey") int nameKey,
							@Param("taxonKey") int taxonKey,
							@Param("syn") Synonym syn
	);

	/**
   * Return synonyms including misapplied names from the synonym relation table.
	 * The Synonym.accepted property is NOT set as it would be highly redundant with the accepted key being the parameter.
	 *
   * We use this call to assemble a complete synonymy
   * and the accepted key is given as the parameter already
   *
	 * @param taxonKey accepted taxon key
	 * @return list of misapplied or heterotypic synonym names ordered by status then homotypic group
	 */
	List<Synonym> listByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonKey") int taxonKey);


	/**
	 * Reads all synonyms including misapplied names by the synonyms name.
	 */
	List<Synonym> listByName(@Param("datasetKey") int datasetKey, @Param("nameKey") int nameKey);

}
