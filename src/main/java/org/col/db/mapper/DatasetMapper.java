package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Dataset;

public interface DatasetMapper {

  Dataset get(@Param("key") int key);

  void create(Dataset dataset);

  void update(Dataset dataset);

  /**
   * Marks a dataset as deleted
   * @param key
   */
  void delete(@Param("key") int key);

  /**
   * Truncates all data from a dataset cascading to all entities incl names, taxa and references.
   * @param key
   */
  void truncateDatasetData(@Param("key") int key);
}
