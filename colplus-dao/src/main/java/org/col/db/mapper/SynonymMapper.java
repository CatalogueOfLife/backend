package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Synonym;

import java.util.List;

/**
 *
 */
public interface SynonymMapper {

  /**
   * Reads a synonym by its name key
   * @param nameKey
   */
  Synonym get(@Param("key") int nameKey);

  /**
   * Creates a new synonym linked to a given name.
   * Note that the name and accepted taxa must exist already and have keys.
   * @param synonym
   */
  void create(@Param("syn") Synonym synonym);

	/**
   * Return misapplied names or heterotypic synonym from the synonym relation table.
   * This does NOT include homotypic names.
   * The Synonym.accepted property is also NOT set as it would be highly redundant
   * and we use this call to assemble a complete synonymy
   * and the accepted key is given as the parameter already
   *
	 * @param taxonKey accepted taxon key
	 * @return list of misapplied or heterotypic synonym names
	 */
	List<Synonym> synonyms(@Param("key") int taxonKey);

}
