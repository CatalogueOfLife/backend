package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.EditorialDecision;

public interface DecisionMapper extends CRUDMapper<EditorialDecision> {
  
  List<EditorialDecision> listBySector(@Param("key") int sectorKey);
  
  List<EditorialDecision> listByDataset(@Param("key") int datasetKey);
  
}
