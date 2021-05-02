package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

public interface DatasetPatchMapper extends DatasetProcessable<ArchivedDataset> {

  void create(@Param("datasetKey") int datasetKey, @Param("obj") ArchivedDataset obj);

  ArchivedDataset get(@Param("datasetKey") int datasetKey, @Param("key") Integer key);

  int update(@Param("datasetKey")  int datasetKey, @Param("obj") ArchivedDataset obj);

  int delete(@Param("datasetKey")  int datasetKey, @Param("key") Integer key);

}
