package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.VernacularName;

public interface VernacularNameMapper {
  
  List<VernacularName> listByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);
  
  VernacularName get(@Param("datasetKey") int datasetKey, @Param("key") int key);
  
  void create(@Param("v") VernacularName vn,
              @Param("taxonId") String taxonId,
              @Param("datasetKey") int datasetKey);
}
