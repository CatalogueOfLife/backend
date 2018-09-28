package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Sector;

public interface SectorMapper {

	List<Sector> list(@Param("key") int colSourceKey);

	Sector get(@Param("key") int key);

	void create(Sector sector);

	int update(Sector sector);

	/**
	 * Marks a source as deleted
	 * 
	 * @param key
	 */
	int delete(@Param("key") int key);

}
