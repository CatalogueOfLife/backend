package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.EditorialDecision;

public interface DecisionMapper extends CRUDIntMapper<EditorialDecision> {
  
  List<EditorialDecision> listBySource(@Param("key") int sourceKey);
  
  List<EditorialDecision> listByDataset(@Param("key") int datasetKey);
  
  /**
   * List all decisions that cannot anymore be linked to subject taxa in the source
   */
  List<EditorialDecision> subjectBroken(@Param("key") Integer sourceKey);
  
}
