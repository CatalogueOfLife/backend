package life.catalogue.db;

import org.apache.ibatis.annotations.Param;

public interface TempNameUsageRelated {

  /**
   * Deletes all that are found in the temp name usage table (see NameUsageMapper).
   */
  int deleteByTemp(@Param("datasetKey") int datasetKey);

}