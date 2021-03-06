package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.CRUD;
import life.catalogue.db.GlobalPageable;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import it.unimi.dsi.fastutil.ints.IntSet;

public interface DatasetMapper extends CRUD<Integer, Dataset>, GlobalPageable<Dataset> {
  int MAGIC_ADMIN_USER_KEY = -42;

  void deletePhysically(@Param("key") int key);

  default void createAll(DatasetWithSettings d) {
    create(d.getDataset());
    updateSettings(d.getKey(), d.getSettings(), d.getDataset().getModifiedBy());
  }

  // for tests only !!!
  void createWithKey(Dataset d);

  DatasetSettings getSettings(@Param("key") int key);

  /**
   * Updates the settings. This requires an existing dataset record with a key to exist!
   * Editors are NOT updated, use the separate {@link #updateEditors} method instead
   */
  void updateSettings(@Param("key") int key, @Param("settings") DatasetSettings settings, @Param("userKey") int userKey);

  IntSet getEditors(@Param("key") int key);

  void updateEditors(@Param("key") int key, @Param("editors") IntSet editors, @Param("userKey") int userKey);

  void addEditor(@Param("key") int key, @Param("editor") int editor, @Param("userKey") int userKey);

  void removeEditor(@Param("key") int key, @Param("editor") int editor, @Param("userKey") int userKey);

  default void updateAll(DatasetWithSettings d) {
    update(d.getDataset());
    updateSettings(d.getKey(), d.getSettings(), d.getDataset().getModifiedBy());
  }

  /**
   * Iterates over all datasets with a cursor, ignoring deleted ones.
   * Includes private datasets.
   *
   * @param filter optional SQL where clause (without WHERE)
   */
  Cursor<Dataset> process(@Nullable @Param("filter") String filter);

  /**
   * @param userKey optional user key so that private datasets for that user will be included in the count.
   *                Use -42 for admins and other roles that should always see all private datasets
   */
  int count(@Param("req") DatasetSearchRequest request, @Param("userKey") Integer userKey);

  /**
   * @param userKey optional user key so that private datasets for that user will be included in the results.
   *                Use -42 for admins and other roles that should always see all private datasets
   */
  List<Dataset> search(@Param("req") DatasetSearchRequest request, @Param("userKey") Integer userKey, @Param("page") Page page);
  
  /**
   * @return list of all dataset keys which have not been deleted
   */
  List<Integer> keys(@Param("origin") DatasetOrigin... origin);

  /**
   * list datasets which have not been imported before, ordered by date created.
   * Includes private datasets.
   *
   * @param limit maximum of datasets to return
   */
  List<Dataset> listNeverImported(int limit);

  /**
   * list datasets which have already been imported before, but need a refresh. The dataset.importFrequency is respected for rescheduling an
   * already imported dataset.
   * Includes private datasets.
   *
   * @param limit maximum of datasets to return
   */
  List<Dataset> listToBeImported(int limit);

  /**
   * @return true if dataset exists and is not deleted
   */
  boolean exists(@Param("key") int key);

  /**
   * @return true if dataset key exists and belongs to a private dataset. Can be a deleted dataset
   */
  boolean isPrivate(@Param("key") int key);

  /**
   * Looks up the dataset key of the latest release for a given project
   * @param key the project key
   * @param publicOnly if true only include public releases
   * @return dataset key of the latest release or null if no release exists
   */
  Integer latestRelease(@Param("key") int key, @Param("public") boolean publicOnly);

  /**
   * Looks up the dataset key of a specific release attempt
   * @param key the project key
   * @param attempt the release attempt
   * @return dataset key of the requested release attempt or null if no release exists that matches
   */
  Integer releaseAttempt(@Param("key") int key, @Param("attempt") int attempt);

  Dataset getByGBIF(@Param("key") UUID key);
  
  /**
   * @return the last import attempt or null if never attempted
   */
  Integer lastImportAttempt(@Param("key") int datasetKey);
  
  int updateLastImport(@Param("key") int key, @Param("attempt") int attempt);

}
