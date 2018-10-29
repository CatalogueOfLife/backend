package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Sector;

public interface SectorMapper extends CRUDMapper<Sector> {

	List<Sector> list(@Param("key") int colSourceKey);
	
	/**
	 * List all sectors that cannot anymore be linked to root taxa in the source
	 */
	List<Sector> rootBroken(@Param("key") Integer colSourceKey);
	
	/**
	 * List all sectors that cannot anymore be linked to attachment points in the draft CoL
	 */
	List<Sector> colBroken(@Param("key") Integer colSourceKey);
}
