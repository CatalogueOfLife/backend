package org.col.db.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.Reference;

/**
 *
 */
public interface ReferenceMapper {

	int count(@Param("datasetKey") Integer datasetKey);

	List<Reference> list(@Param("datasetKey") Integer datasetKey, @Param("page") Page page);

	List<Integer> listByTaxon(@Param("taxonKey") int taxonKey);

	/**
   * Selects a number of distinct references by their keys
   * @param keys must contain at least one value, not allowed to be empty !!!
   */
  List<Reference> listByKeys(@Param("keys") Set<Integer> keys);

	Integer lookupKey(@Param("id") String id, @Param("datasetKey") int datasetKey);

	Reference get(@Param("key") int key);

	void create(Reference name);

	/**
	 * Links a reference to a taxon
	 */
	void linkToTaxon(@Param("datasetKey") int datasetKey, @Param("taxonKey") int taxonKey, @Param("refKey") int refKey);
}
