package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.Distribution;

public interface DistributionMapper {

	List<Distribution> getDistributions(@Param("datasetKey") int datasetKey,
	    @Param("taxonId") String taxonId);

	List<Distribution> getDistributionsByTaxonKey(@Param("taxonKey") int taxonKey);

	Distribution getByKey(@Param("key") int ikey);

	void create(Distribution vn);

}
