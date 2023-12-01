package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface DecisionMapper extends BaseDecisionMapper<EditorialDecision, DecisionSearchRequest> {

  EditorialDecision getBySubject(@Param("datasetKey") int datasetKey,
                      @Param("subjectDatasetKey") int subjectDatasetKey,
                      @Param("id") String id);

  /**
   * Select a decision in a release by its released usage id.
   * @param key taxon or synonym usage id in the release, not the subject one
   */
  EditorialDecision getByReleasedUsage(@Param("key") DSID<String> key);

  /**
   * Process all decisions for a given subject dataset, optionally filtered by a project
   * @param datasetKey the projects datasetKey
   * @param subjectDatasetKey the decision subjects datasetKey
   */
  Cursor<EditorialDecision> processDecisions(@Nullable @Param("datasetKey") Integer datasetKey,
                                @Param("subjectDatasetKey") int subjectDatasetKey);

  /**
   * List all distinct project dataset keys that have at least one decision on the given subject dataset key.
   * This will only return dataset keys of PROJECT, not RELEASE datasets.
   * @param subjectDatasetKey the decision subjects datasetKey
   */
  List<Integer> listProjectKeys(@Param("subjectDatasetKey") int subjectDatasetKey);

  List<EditorialDecision> listStaleAmbiguousUpdateDecisions(@Param("datasetKey") int projectKey, @Nullable @Param("subjectDatasetKey") Integer subjectDatasetKey);
}
