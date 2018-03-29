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
	 * @param taxonKey accepted taxon key
	 * @return list of synonym names, ordered by their basionymKey
	 */
	List<Synonym> synonyms(@Param("key") int taxonKey);

}
