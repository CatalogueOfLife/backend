package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

public interface DatasetPatchMapper extends DatasetProcessable<Dataset> {

  void create(@Param("datasetKey") int datasetKey, @Param("obj") Dataset obj);

  Dataset get(@Param("datasetKey") int datasetKey, @Param("key") Integer key);

  int update(@Param("datasetKey")  int datasetKey, @Param("obj") Dataset obj);

  int delete(@Param("datasetKey")  int datasetKey, @Param("key") Integer key);

}
