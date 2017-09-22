package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.DatasetMetrics;

import java.util.Date;
import java.util.List;

/**
 * The MyBatis mapper interface for DatasetMetrics.
 */
public interface DatasetMetricsMapper {

  DatasetMetrics get(@Param("key") int key);

  List<DatasetMetrics> list(@Param("key") int key);

  void insert(@Param("key") int key, @Param("downloaded") Date downloaded);

}
