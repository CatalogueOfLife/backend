package org.col.db.mapper;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.vocab.ImportState;

/**
 * The MyBatis mapper interface for DatasetImport.
 */
public interface DatasetImportMapper {

  /**
   * Get the latest import for a dataset
   */
  DatasetImport last(@Param("key") int datasetKey);

  /**
   * Get the latest successful import for a dataset
   */
  DatasetImport lastSuccessful(@Param("key") int datasetKey);

  /**
   * Count all imports by their state
   */
  int count(@Param("states") Collection<ImportState> states);

  /**
   * List all imports optionally filtered by their state
   */
  List<DatasetImport> list(@Param("states") @Nullable Collection<ImportState> states, @Param("page") Page page);

  /**
   * List all current and historical imports for a dataset
   */
  List<DatasetImport> listByDataset(@Param("key") int datasetKey);

  /**
   * Generates new dataset metrics based on the current data.
   * This method will only populate the count fields but no other fields.
   */
  DatasetImport metrics(@Param("key") int datasetKey);

  void create(@Param("di") DatasetImport datasetImport);

  void update(@Param("di") DatasetImport datasetImport);
}
