package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Name;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;

import java.util.List;

/**
 *
 */
public interface SynonymMapper {

  /**
   * Reads a synonym (both hetero- and homotypical) by its name.
	 * Warning, the main synonym name property is not set cause it is expected to exist already
	 */
  Synonym getByName(@Param("name") Name name);

  /**
   * Creates a new synonym linked to a given name.
   * Note that the name and accepted taxa must exist already and have keys.
   * @param synonym
   */
  void create(@Param("syn") Synonym synonym);

	/**
   * Return misapplied names or heterotypic synonym from the synonym relation table.
   * This does NOT include homotypic names and
	 * the Synonym.accepted property is NOT set as it would be highly redundant with the accepted key being the parameter.
	 *
   * We use this call to assemble a complete synonymy
   * and the accepted key is given as the parameter already
   *
	 * @param taxonKey accepted taxon key
	 * @return list of misapplied or heterotypic synonym names
	 */
	List<Synonym> synonyms(@Param("key") int taxonKey);

}
