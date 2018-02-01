package org.col.dw.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.dw.api.Name;
import org.col.dw.api.NameSearch;
import org.col.dw.api.Page;
import org.col.dw.db.mapper.temp.NameSearchResultTemp;

/**
 *
 */
public interface NameMapper {

	int count(@Param("datasetKey") Integer datasetKey);

	List<Name> list(@Param("datasetKey") Integer datasetKey, @Param("page") Page page);

	Integer lookupKey(@Param("id") String id, @Param("datasetKey") int datasetKey);

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

	List<NameSearchResultTemp> search(@Param("nameSearch") NameSearch nameSearch,
	    @Param("page") Page page);
}
