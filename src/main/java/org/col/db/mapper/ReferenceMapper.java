package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Reference;

/**
 *
 */
public interface ReferenceMapper {

  Reference getByInternalKey(@Param("key") int key);

  Reference get(@Param("dkey") int datasetKey, @Param("key") String key);

  /**
   * Returns the reference of the description act for the given name key.
   */
  Reference getPublishedIn(@Param("datasetKey") int datasetKey, @Param("nameKey") String nameKey);

  void insert(Reference name);

}

