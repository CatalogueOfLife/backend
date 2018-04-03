package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Name;
import org.col.api.model.NameSearch;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;

import java.util.List;

/**
 *
 */
public interface NameMapper {

	int count(@Param("datasetKey") Integer datasetKey);

	List<Name> list(@Param("datasetKey") Integer datasetKey, @Param("page") Page page);

	Integer lookupKey(@Param("id") String id, @Param("datasetKey") int datasetKey);

	Name get(@Param("key") int key);

	void create(Name name);

	/**
	 * @param taxonKey
	 *          accepted taxon key
	 * @return list of synonym names, ordered by their basionymKey
	 */
	List<Name> synonyms(@Param("key") int taxonKey);

	/**
	 * Lists all homotypic basionymGroup based on the same basionym
	 * 
	 * @return
	 */
	List<Name> basionymGroup(@Param("key") int key);

	int countSearchResults(@Param("q") NameSearch query);

	List<NameUsage> search(@Param("q") NameSearch query,
                         @Param("page") Page page);
}
