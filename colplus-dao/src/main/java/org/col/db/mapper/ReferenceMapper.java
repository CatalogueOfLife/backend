package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.model.ReferenceWithPage;

import java.util.List;
import java.util.Set;

/**
 *
 */
public interface ReferenceMapper {

	int count(@Param("datasetKey") int datasetKey);

	List<Reference> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  /**
   * Selects a number of distinct references by their keys
   * @param keys must contain at least one value, not allowed to be empty !!!
   */
  List<Reference> listByKeys(@Param("keys") Set<Integer> keys);

	Integer lookupKey(String id, int datasetKey);

	Reference get(@Param("key") int key);

	/**
	 * Returns the reference of the description act for the given name key.
	 */
	ReferenceWithPage getPublishedIn(@Param("nameKey") int nameKey);

	void create(Reference name);

}
