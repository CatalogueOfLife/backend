package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.Name;
import org.col.api.NameSearch;
import org.col.api.NameSearchResult;
import org.col.api.Page;

/**
 *
 */
public interface NameMapper {

	Integer lookupKey(@Param("datasetKey") int datasetKey, @Param("id") String id);

	int count(@Param("datasetKey") int datasetKey);

	List<Name> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

	Name get(@Param("key") int key);

	void create(Name name);

	void addSynonym(@Param("datasetKey") int datasetKey,
	    @Param("key") int taxonKey,
	    @Param("nameKey") int synonymNameKey);

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

	int countSearchResults(@Param("nameSearch") NameSearch nameSearch);

	List<NameSearchResult> search(@Param("nameSearch") NameSearch nameSearch, @Param("page") Page page);
}
