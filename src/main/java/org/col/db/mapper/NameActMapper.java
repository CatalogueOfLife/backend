package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.NameAct;

import java.util.List;

/**
 *
 */
public interface NameActMapper {

  /**
   * Returns the list of nomenclatural acts for a single name.
   */
  List<NameAct> listByName(@Param("datasetKey") int datasetKey, @Param("nameKey") String nameKey);

  /**
   * Returns the list of nomenclatural acts for a whole homotypic group.
   */
  List<NameAct> listByHomotypicGroup(@Param("datasetKey") int datasetKey, @Param("nameKey") String nameKey);

  void create(NameAct act);

}

