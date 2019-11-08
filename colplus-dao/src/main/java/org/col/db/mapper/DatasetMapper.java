package org.col.db.mapper;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.search.DatasetSearchRequest;
import org.col.db.CRUD;
import org.col.db.GlobalPageable;

public interface DatasetMapper extends CRUD<Integer, Dataset>, GlobalPageable<Dataset> {
  
  int count(@Param("req") DatasetSearchRequest request);
  
  /**
   * Iterates over all datasets of a given dataset and processes them with the supplied handler.
   * @param filter optional SQL where clause (without WHERE)
   */
  void process(@Nullable @Param("filter") String filter, ResultHandler<Dataset> handler);

  List<Dataset> search(@Param("req") DatasetSearchRequest request, @Param("page") Page page);
  
  /**
   * @return list of all dataset keys which have not been deleted
   */
  List<Integer> keys();

  /**
   * list datasets which have not been imported before, ordered by date created.
   *
   * @param limit maximum of datasets to return
   */
  List<Dataset> listNeverImported(int limit);

  /**
   * list datasets which have already been imported before, but need a refresh. The dataset.importFrequency is respected for rescheduling an
   * already imported dataset
   *
   * @param limit maximum of datasets to return
   */
  List<Dataset> listToBeImported(int limit);

  /**
   * @return dataset key if dataset exists and is not deleted, null otherwise
   */
  Integer exists(@Param("key") int key);

  Dataset getByGBIF(@Param("key") UUID key);
  
  Dataset getByCatalogue(@Param("key") int key, @Param("catalogueKey") int catalogueKey);

  /**
   * @return the last import attempt or null if never attempted
   */
  Integer lastImportAttempt(@Param("key") int datasetKey);
  
  int updateLastImport(@Param("key") int key, @Param("attempt") int attempt);

}
