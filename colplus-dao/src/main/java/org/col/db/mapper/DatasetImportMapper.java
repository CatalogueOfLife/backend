package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.vocab.ImportState;

/**
 * The MyBatis mapper interface for DatasetImport.
 */
public interface DatasetImportMapper {

  DatasetImport last(@Param("key") int datasetKey);

  DatasetImport lastSuccessful(@Param("key") int datasetKey);

  int count(@Param("state") ImportState state);

  List<DatasetImport> list(@Param("state") ImportState state, @Param("page") Page page);

  List<DatasetImport> listByDataset(@Param("key") int datasetKey);

  /**
   * Generates new dataset metrics based on the current data.
   * This method will only populate the count fields but no other fields.
   */
  DatasetImport metrics(@Param("key") int datasetKey);

  void create(@Param("di") DatasetImport datasetImport);

  void update(@Param("di") DatasetImport datasetImport);
}
