package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.VernacularName;

public interface VernacularNameMapper {

	List<VernacularName> getVernacularNamesForTaxon(@Param("taxonKey") int taxonKey,
	    @Param("datasetKey") int datasetKey);

	VernacularName getByInternalKey(@Param("key") int ikey);

	void create(VernacularName vn);

}
