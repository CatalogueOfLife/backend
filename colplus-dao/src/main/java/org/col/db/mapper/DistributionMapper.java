package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.Distribution;

public interface DistributionMapper {

	List<Distribution> listByTaxon(@Param("taxonKey") int taxonKey);

	Distribution get(@Param("key") int ikey);

	void create(Distribution vn);

}
