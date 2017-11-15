package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Distribution;

import java.util.List;

public interface DistributionMapper {

	List<Distribution> listByTaxon(@Param("taxonKey") int taxonKey);

	Distribution get(@Param("key") int key);

	void create(@Param("d") Distribution d,
	  @Param("taxonKey") int taxonKey,
    @Param("datasetKey") int datasetKey
  );

}
