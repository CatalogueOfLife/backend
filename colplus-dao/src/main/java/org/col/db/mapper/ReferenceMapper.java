package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.Page;
import org.col.api.PagedReference;
import org.col.api.Reference;

/**
 *
 */
public interface ReferenceMapper {

	Reference get(@Param("key") int key);

	int count(@Param("datasetKey") int datasetKey);

	List<Reference> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

	List<PagedReference> listByTaxon(@Param("taxonKey") int taxonKey);

	List<PagedReference> listByVernacularNamesOfTaxon(@Param("taxonKey") int taxonKey);

	List<PagedReference> listByDistributionOfTaxon(@Param("taxonKey") int taxonKey);

	/**
	 * Returns the reference of the description act for the given name key.
	 */
	PagedReference getPublishedIn(@Param("datasetKey") int datasetKey, @Param("nameKey") int nameKey);

	void create(Reference name);

}
