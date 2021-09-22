package life.catalogue.db;

import org.apache.ibatis.annotations.Param;

/**
 * Copy interface for managed or released datasets only that live on their own partition table
 * and do not share their table with other datasets.
 */
public interface CopyDataset {
  
  int copyDataset(@Param("datasetKey") int datasetKey, @Param("newDatasetKey") int newDatasetKey, @Param("mapIds") boolean mapIds);

}
