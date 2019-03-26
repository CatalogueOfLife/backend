package org.col.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.EditorialDecision;
import org.col.db.CRUDInt;

public interface DecisionMapper extends CRUDInt<EditorialDecision> {
  
  List<EditorialDecision> list(@Nullable @Param("datasetKey") Integer datasetKey, @Nullable @Param("id") String id);
  
  /**
   * List all decisions that cannot anymore be linked to subject taxa in the source
   * @param datasetKey optional dataset filter
   */
  List<EditorialDecision> subjectBroken(@Param("datasetKey") Integer datasetKey);
  
}
