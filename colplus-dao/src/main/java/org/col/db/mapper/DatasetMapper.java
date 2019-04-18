package org.col.db.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.search.DatasetSearchRequest;

public interface DatasetMapper extends GlobalCRUDMapper<Dataset> {

  int count(@Param("req") DatasetSearchRequest request);

  List<Dataset> search(@Param("req") DatasetSearchRequest request, @Param("page") Page page);
  
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

  int updateLastImport(@Param("key") int key, @Param("attempt") int attempt);

}
