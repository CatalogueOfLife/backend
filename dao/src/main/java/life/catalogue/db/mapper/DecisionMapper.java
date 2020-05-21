package life.catalogue.db.mapper;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.db.CopyDataset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface DecisionMapper extends BaseDecisionMapper<EditorialDecision, DecisionSearchRequest>, CopyDataset {

  EditorialDecision getBySubject(@Param("datasetKey") int datasetKey,
                      @Param("subjectDatasetKey") int subjectDatasetKey,
                      @Param("id") String id);

  /**
   * Process all decisions for a given subject dataset and catalogue
   * @param datasetKey the catalogues datasetKey
   * @param subjectDatasetKey the decision subjects datasetKey
   */
  Cursor<EditorialDecision> processDecisions(@Param("datasetKey") int datasetKey,
                                @Param("subjectDatasetKey") int subjectDatasetKey);

}
