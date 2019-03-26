package org.col.db.mapper;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Sector;
import org.col.db.CRUDInt;

public interface SectorMapper extends CRUDInt<Sector> {
  
  List<Sector> list(@Nullable @Param("datasetKey") Integer datasetKey);
  
  /**
   * List all sectors which have a targetID within the given sector.
   */
  List<Sector> listChildSectors(@Param("key") int sectorKey);

  /**
   * List all sectors that cannot anymore be linked to subject taxa in the source
   */
  List<Sector> subjectBroken(@Param("datasetKey") int datasetKey);
  
  /**
   * List all sectors from a source dataset that cannot anymore be linked to attachment points in the draft CoL
   */
  List<Sector> targetBroken(@Param("datasetKey") int datasetKey);
}
