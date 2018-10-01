package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Sector;

public interface SectorMapper extends CRUDMapper<Sector> {

	List<Sector> list(@Param("key") int colSourceKey);
	
}
