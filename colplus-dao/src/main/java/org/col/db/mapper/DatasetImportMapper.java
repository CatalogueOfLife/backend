package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.DatasetImport;

import java.util.List;

/**
 * The MyBatis mapper interface for DatasetImport.
 */
public interface DatasetImportMapper {

  DatasetImport last(@Param("key") int datasetKey);

  DatasetImport lastSuccessful(@Param("key") int datasetKey);

  List<DatasetImport> list(@Param("key") int datasetKey);

  /**
   * Generates new dataset metrics based on the current data.
   * This method will only populate the count fields but no other fields.
   */
  DatasetImport metrics(@Param("key") int datasetKey);

  void create(@Param("di") DatasetImport datasetImport);

}
