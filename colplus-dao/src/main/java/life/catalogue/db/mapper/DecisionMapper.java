package life.catalogue.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;

public interface DecisionMapper extends CRUD<Integer, EditorialDecision>, DatasetPageable<EditorialDecision>, ProcessableDataset<EditorialDecision> {
  
  /**
   * List all decisions from a subject taxa in the source
   * @param datasetKey catalogs dataset key
   * @param subjectDatasetKey optional dataset filter
   */
  List<EditorialDecision> listBySubjectDataset(@Param("datasetKey") int datasetKey,
                                               @Nullable @Param("subjectDatasetKey") Integer subjectDatasetKey,
                                               @Nullable @Param("id") String id);
  
  /**
   * List all decisions that cannot anymore be linked to subject taxa in the source
   * @param datasetKey catalogs dataset key
   * @param subjectDatasetKey optional dataset filter
   */
  List<EditorialDecision> subjectBroken(@Param("datasetKey") int datasetKey,
                                        @Nullable @Param("subjectDatasetKey") Integer subjectDatasetKey);
  
}
