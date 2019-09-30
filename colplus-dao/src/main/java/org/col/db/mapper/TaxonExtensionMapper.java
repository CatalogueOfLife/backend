package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.GlobalEntity;
import org.col.api.model.TaxonExtension;

public interface TaxonExtensionMapper<T extends GlobalEntity> {

	List<T> listByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);

	T get(@Param("datasetKey") int datasetKey, @Param("key") int key);

	void create(@Param("obj") T object,
              @Param("taxonId") String taxonId,
              @Param("datasetKey") int datasetKey);
	
	/**
	 * Iterates over all entities of a given dataset and processes them with the supplied handler.
	 */
	void processDataset(@Param("datasetKey") int datasetKey, ResultHandler<TaxonExtension<T>> handler);
	
}
