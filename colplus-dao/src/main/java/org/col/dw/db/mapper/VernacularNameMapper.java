package org.col.dw.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.dw.api.VernacularName;

import java.util.List;

public interface VernacularNameMapper {

	List<VernacularName> listByTaxon(@Param("taxonKey") int taxonKey);

	VernacularName get(@Param("key") int key);

	void create(@Param("vernacular") VernacularName vn,
	    @Param("taxonKey") int taxonKey,
	    @Param("datasetKey") int datasetKey);

}
