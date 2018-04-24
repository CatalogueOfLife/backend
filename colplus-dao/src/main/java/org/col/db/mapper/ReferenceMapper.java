package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.Reference;

import java.util.List;
import java.util.Set;

/**
 *
 */
public interface ReferenceMapper {

	int count(@Param("datasetKey") Integer datasetKey);

	List<Reference> list(@Param("datasetKey") Integer datasetKey, @Param("page") Page page);

	List<Integer> taxonReferences(@Param("taxonKey") int taxonKey);

	/**
   * Selects a number of distinct references by their keys
   * @param keys must contain at least one value, not allowed to be empty !!!
   */
  List<Reference> listByKeys(@Param("keys") Set<Integer> keys);

	Integer lookupKey(String id, int datasetKey);

	Reference get(@Param("key") int key);

	void create(Reference name);
}
