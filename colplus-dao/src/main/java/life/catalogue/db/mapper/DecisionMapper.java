package life.catalogue.db.mapper;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.Searchable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface DecisionMapper extends CRUD<Integer, EditorialDecision>, DatasetPageable<EditorialDecision>,
    ProcessableDataset<EditorialDecision>, Searchable<EditorialDecision, DecisionSearchRequest> {

  /**
   * Process all decisions for a given subject dataset and catalogue
   * @param datasetKey the catalogues datasetKey
   * @param subjectDatasetKey the decision subjects datasetKey
   */
  Cursor<EditorialDecision> processDecisions(@Param("datasetKey") int datasetKey,
                                @Param("subjectDatasetKey") int subjectDatasetKey);

}
