package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Distribution;

public interface DistributionMapper {

	List<Distribution> listByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);

	Distribution get(@Param("datasetKey") int datasetKey, @Param("key") int key);

	void create(@Param("d") Distribution distribution,
	    @Param("taxonId") String taxonId,
	    @Param("datasetKey") int datasetKey);

}
