package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Reference;

/**
 *
 */
public interface ReferenceMapper {

  Reference getByKey(@Param("key") int key);

  Reference get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  /**
   * Returns the reference of the description act for the given name key.
   */
  Reference getPublishedIn(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);

  void insert(Reference name);

}

