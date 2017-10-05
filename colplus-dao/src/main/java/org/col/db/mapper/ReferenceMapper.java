package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Page;
import org.col.api.Reference;

import java.util.List;

/**
 *
 */
public interface ReferenceMapper {

  int count(@Param("datasetKey") int datasetKey);

  List<Reference> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  Reference getByKey(@Param("key") int key);

  Reference get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  /**
   * Returns the reference of the description act for the given name key.
   */
  Reference getPublishedIn(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);

  void create(Reference name);

}

