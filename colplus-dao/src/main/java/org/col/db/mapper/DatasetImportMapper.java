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
   * Count all imports by their state
   */
  int count(@Param("states") Collection<ImportState> states);

  /**
   * List all imports optionally filtered by their state
   */
  List<DatasetImport> list(@Param("states") @Nullable Collection<ImportState> states, @Param("page") Page page);

  /**
   * List current and historical imports for a dataset ordered by attempt from last to historical.
   * Optionally filtered and limited, e.g. by one to get the last only.
   */
  List<DatasetImport> listByDataset(@Param("key") int datasetKey, @Param("state") @Nullable ImportState state, @Param("limit") int limit);

  /**
   * Generates new dataset metrics based on the current data.
   * This method will only populate the count fields but no other fields.
   */
  DatasetImport metrics(@Param("key") int datasetKey);

  void create(@Param("di") DatasetImport datasetImport);

  void update(@Param("di") DatasetImport datasetImport);
}
