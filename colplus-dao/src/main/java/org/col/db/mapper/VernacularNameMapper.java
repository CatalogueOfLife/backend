package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.VernacularName;

public interface VernacularNameMapper {

	List<VernacularName> getVernacularNames(@Param("datasetKey") int datasetKey,
	    @Param("taxonId") String taxonId);

	List<VernacularName> getVernacularNamesByTaxonKey(@Param("taxonKey") int taxonKey);

	VernacularName getByKey(@Param("key") int ikey);

	void create(VernacularName vn);

}
