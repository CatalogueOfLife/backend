package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.VernacularName;

public interface VernacularNameMapper {

	List<VernacularName> listByTaxon(@Param("taxonKey") int taxonKey);

	VernacularName get(@Param("key") int key);

	void create(@Param("vernacular") VernacularName vn,
	    @Param("taxonKey") int taxonKey,
	    @Param("datasetKey") int datasetKey);

}
