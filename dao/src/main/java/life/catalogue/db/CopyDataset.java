package life.catalogue.db;

import org.apache.ibatis.annotations.Param;

public interface CopyDataset {
  
  int copyDataset(@Param("datasetKey") int datasetKey, @Param("newDatasetKey") int newDatasetKey, @Param("mapIds") boolean mapIds);

}
