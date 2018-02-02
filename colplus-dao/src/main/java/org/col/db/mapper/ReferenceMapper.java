package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.PagedReference;
import org.col.api.model.Reference;

/**
 *
 */
public interface ReferenceMapper {

	int count(@Param("datasetKey") int datasetKey);

	List<Reference> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

	Integer lookupKey(String id, int datasetKey);

	Reference get(@Param("key") int key);

	List<PagedReference> listByTaxon(@Param("taxonKey") int taxonKey);

	List<PagedReference> listByVernacularNamesOfTaxon(@Param("taxonKey") int taxonKey);

	List<PagedReference> listByDistributionOfTaxon(@Param("taxonKey") int taxonKey);

	/**
	 * Returns the reference of the description act for the given name key.
	 */
	PagedReference getPublishedIn(@Param("nameKey") int nameKey);

	void create(Reference name);

}
