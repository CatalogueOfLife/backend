package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.db.CRUD;
import life.catalogue.db.GlobalPageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * The dataset mappers create method expects the key to be provided.
 * Unless you know exactly what you are doing please use the DatasetDAO to create, modify or delete datasets.
 */
public interface DatasetMapper extends CRUD<Integer, Dataset>, GlobalPageable<Dataset>, DatasetAgentMapper {
  int MAGIC_ADMIN_USER_KEY = -42;

  /**
   * Updates the modifiedBy to the given user and modified to now.
   * @param key datasetKey
   */
  void updateModifiedBy(@Param("key") int key, @Param("user") int user);

  void deletePhysically(@Param("key") int key);

  default void createAll(DatasetWithSettings d) {
    create(d.getDataset());
    updateSettings(d.getKey(), d.getSettings(), d.getDataset().getModifiedBy());
  }

  DatasetSettings getSettings(@Param("key") int key);

  /**
   * Updates the settings. This requires an existing dataset record with a key to exist!
   * Editors are NOT updated, use the separate {@link #updateEditors} method instead
   */
  void updateSettings(@Param("key") int key, @Param("settings") DatasetSettings settings, @Param("userKey") int userKey);


  IntSet getReviewer(@Param("key") int key);

  void updateReviewer(@Param("key") int key, @Param("reviewer") IntSet reviewer, @Param("userKey") int userKey);

  void addReviewer(@Param("key") int key, @Param("reviewer") int reviewer, @Param("userKey") int userKey);

  void removeReviewer(@Param("key") int key, @Param("reviewer") int reviewer, @Param("userKey") int userKey);

  void removeReviewerEverywhere(@Param("reviewer") int reviewer, @Param("userKey") int userKey);


  IntSet getEditors(@Param("key") int key);

  void updateEditors(@Param("key") int key, @Param("editor") IntSet editors, @Param("userKey") int userKey);

  void addEditor(@Param("key") int key, @Param("editor") int editor, @Param("userKey") int userKey);

  void removeEditor(@Param("key") int key, @Param("editor") int editor, @Param("userKey") int userKey);

  void removeEditorEverywhere(@Param("editor") int editor, @Param("userKey") int userKey);

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
   * List all releases of a project, including deleted and private ones.
   */
  List<Dataset> listReleases(@Param("projectKey") int projectKey);

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
  List<DatasetDI> listNeverImported(@Param("limit") int limit);

  /**
   * list datasets which have already been imported before, but need a refresh. The dataset.importFrequency is respected for rescheduling an
   * already imported dataset.
   * Includes private datasets.
   *
   * @param limit maximum of datasets to return
   * @param defaultFrequency number in days to between import attempts when no explicit frequency is configured
   */
  List<DatasetDI> listToBeImported(@Param("defaultFrequency") int defaultFrequency, @Param("limit") int limit);

  class DatasetDI extends Dataset {
    private ImportState state;
    private LocalDateTime finished;

    public ImportState getState() {
      return state;
    }

    public void setState(ImportState state) {
      this.state = state;
    }

    public LocalDateTime getFinished() {
      return finished;
    }

    public void setFinished(LocalDateTime finished) {
      this.finished = finished;
    }
  }

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
   * This looks up the public release just before the given one, ignoring any intermediate private releases.
   * @param key release dataset key
   */
  Integer previousRelease(@Param("key") int key);

  /**
   * Looks up the dataset key of a specific release attempt
   * @param key the project key
   * @param attempt the release attempt
   * @return dataset key of the requested release attempt or null if no release exists that matches
   */
  Integer releaseAttempt(@Param("key") int key, @Param("attempt") int attempt);

  Dataset getByGBIF(@Param("key") UUID key);

  /**
   * Lists all dataset keys of non deleted datasets that have a UUID GBIF registry key.
   */
  List<Integer> listGBIF();

  /**
   * @return the last import attempt or null if never attempted
   */
  Integer lastImportAttempt(@Param("key") int datasetKey);
  
  int updateLastImport(@Param("key") int key, @Param("attempt") int attempt);

  /**
   * @param limit optional limit to consider when looking for the maximum. If null the global maximum is returned, if existing the maximum key below the given imit
   */
  Integer getMaxKey(@Param("limit") Integer limit);

}
