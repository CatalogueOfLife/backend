package life.catalogue.db.mapper;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import javax.annotation.Nullable;

public interface DecisionMapper extends BaseDecisionMapper<EditorialDecision, DecisionSearchRequest> {

  EditorialDecision getBySubject(@Param("datasetKey") int datasetKey,
                      @Param("subjectDatasetKey") int subjectDatasetKey,
                      @Param("id") String id);

  /**
   * Process all decisions for a given subject dataset, optionally filtered by a project
   * @param datasetKey the projects datasetKey
   * @param subjectDatasetKey the decision subjects datasetKey
   */
  Cursor<EditorialDecision> processDecisions(@Nullable @Param("datasetKey") Integer datasetKey,
                                @Param("subjectDatasetKey") int subjectDatasetKey);

}
