package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.GlobalPageable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface DatasetMapper extends CRUD<Integer, Dataset>, GlobalPageable<Dataset> {
  
  /**
   * Copies a given dataset key into the archive with the given catalogueKey
   * @param key
   * @param catalogueKey
   */
  void createArchive(@Param("key") int key, @Param("catalogueKey") int catalogueKey);
  
  Dataset getArchive(@Param("key") int key, @Param("catalogueKey") int catalogueKey);

  int count(@Param("req") DatasetSearchRequest request);
  
  /**
   * Iterates over all datasets of a given dataset and processes them with the supplied handler.
   * @param filter optional SQL where clause (without WHERE)
   * @param catalogueKey optional filter returning only datasets being constituents of the given catalogueKey
   */
  Cursor<Dataset> process(@Nullable @Param("filter") String filter,
                          @Nullable @Param("catalogueKey") Integer catalogueKey);

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
  
  /**
   * @return the last import attempt or null if never attempted
   */
  Integer lastImportAttempt(@Param("key") int datasetKey);
  
  int updateLastImport(@Param("key") int key, @Param("attempt") int attempt);

}
