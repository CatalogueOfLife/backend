package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DSID;
import org.col.api.model.DatasetScopedEntity;
import org.col.api.model.TaxonExtension;

public interface TaxonExtensionMapper<T extends DatasetScopedEntity<Integer>> extends ProcessableDataset<TaxonExtension<T>> {

	T get(@Param("key") DSID<Integer> key);
	
	List<T> listByTaxon(@Param("key") DSID<String> key);
	
	void create(@Param("obj") T object,
              @Param("taxonId") String taxonId);
	
}
