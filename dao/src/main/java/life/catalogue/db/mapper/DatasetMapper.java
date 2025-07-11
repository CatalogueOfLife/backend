package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.CRUD;
import life.catalogue.db.GlobalPageable;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import it.unimi.dsi.fastutil.ints.IntSet;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The dataset mappers create method expects the key to be provided.
 * Unless you know exactly what you are doing please use the DatasetDAO to create, modify or delete datasets.
 */
public interface DatasetMapper extends CRUD<Integer, Dataset>, GlobalPageable<Dataset>, DatasetAgentMapper {
  Logger LOG = LoggerFactory.getLogger(DatasetMapper.class);

  int MAGIC_ADMIN_USER_KEY = -42;

  DatasetSimple getSimple(@Param("key") int key);

  void deletePhysically(@Param("key") int key);

  default void createAll(DatasetWithSettings d) {
    create(d.getDataset());
    updateSettings(d.getKey(), d.getSettings(), d.getDataset().getModifiedBy());
  }

  void createWithID(Dataset obj);

  /**
   * List all keys equal or above the given minimum.
   * Includes deleted, private and temporary datasets!
   */
  List<Integer> keysAbove(@Param("min") int minID, @Nullable @Param("olderThan") LocalDateTime olderThan);

  DatasetSettings getSettings(@Param("key") int key);

  /**
   * Updates the settings. This requires an existing dataset record with a key to exist!
   * Editors are NOT updated, use the separate {@link #updateEditors} method instead
   */
  void updateSettings(@Param("key") int key, @Param("settings") DatasetSettings settings, @Param("userKey") int userKey);

  /**
   * Updates the taxonomic group scope of a given dataset.
   * It adds missing parental groups to the set before storing it with the dataset.
   */
  default void updateTaxonomicGroupScope(int key, Set<TaxGroup> groups) {
    Set<TaxGroup> allGroups = EnumSet.noneOf(TaxGroup.class);
    // expand group to include all parents
    for (TaxGroup g : groups) {
      allGroups.add(g);
      allGroups.addAll(g.classification());
    }
    LOG.debug("Store {} taxon groups for dataset {} ", allGroups.size(), key);
    _updateTaxonomicGroupScope(key, allGroups);
  }

  void _updateTaxonomicGroupScope(@Param("key") int key, @Param("groups") Set<TaxGroup> groups);

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

  /**
   * Removes all access control keys for all users to the given dataset
   * @param key dataset key to clear
   */
  void clearACL(@Param("key") int key, @Param("userKey") int userKey);

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
  List<Dataset> search(@Param("req") DatasetSearchRequest request,
                       @Param("userKey") Integer userKey,
                       @Param("page") Page page
  );

  default List<DatasetSimple> suggest(@Param("q") String query,
                              @Param("contributesTo") Integer contributesTo,
                              @Param("inclMerge") boolean inclMergeSources,
                              @Param("limit") int limit) {
    try {
      var key = Integer.parseInt(query);
      return suggestInternal(null, key, contributesTo, inclMergeSources, limit);
    } catch (NumberFormatException e) {
      return suggestInternal(query, null, contributesTo, inclMergeSources, limit);
    }
  }

  /**
   * Filters datasets to only list those that contribute as a source dataset with at least one sector to a given project.
   * @param contributesTo project or release to contribute to, i.e. be a source. If null all datasets are candidates
   * @param datasetKey the exact dataset key of the source to suggest. When known can be used to force the correct lookup
   */
  List<DatasetSimple> suggestInternal(@Param("q") String query,
                              @Param("key") Integer datasetKey,
                              @Param("contributesTo") Integer contributesTo,
                              @Param("inclMerge") boolean inclMergeSources,
                              @Param("limit") int limit
  );

  /**
   * List all dataset keys filtered by a search request.
   * Contrary to the regular search this will include private datasets.
   */
  List<Integer> searchKeys(@Param("req") DatasetSearchRequest request, @Param("userKey") Integer userKey);

  /**
   * List all dataset keys of (x)releases for the given project.
   * This includes both private and public datasets.
   */
  List<Integer> listReleaseKeys(@Param("projectKey") int projectKey);

  /**
   * List all releases of a project, including deleted and private ones.
   * Ordered chronologically starting with the first release. Sorted by attempt, key
   */
  List<Dataset> listReleases(@Param("projectKey") int projectKey);

  /**
   * Same as above, but returning just a minimal object which is much quicker to load.
   * Ordered chronologically starting with the first release. Sorted by attempt, key
   */
  List<DatasetRelease> listReleasesQuick(@Param("projectKey") int projectKey);

  /**
   * Retrieves a release quickly with minimal information.
   * @return the release or null if no release exists for the given dataset key or the dataset is not a release dataset.
   */
  DatasetRelease getRelease(@Param("key") int key);

  /**
   * Looks for potential duplicates of a dataset by aggregating them on title and description.
   * This method avoids temporary datasets used e.g. for validation.
   *
   * @param minCount minimum number of datasets to be considered a duplicate.
   * @param gbifPublisherKey optional publisher filter
   *
   * @return list of duplicate titles, each with all dataset keys listed in it.
   */
  List<Duplicate.IntKeys> duplicates(@Param("minCount") Integer minCount, @Param("gbifPublisherKey") UUID gbifPublisherKey);

  /**
   * @param origin optional dataset origin filter, combined with OR if multiple
   * @return list of all dataset keys which have not been deleted
   */
  default List<Integer> keys(@Param("origin") DatasetOrigin... origin) {
    DatasetSearchRequest req = new DatasetSearchRequest();
    if (origin != null) {
      req.setOrigin(List.of(origin));
    }
    return searchKeys(req, MAGIC_ADMIN_USER_KEY);
  }

  /**
   * @return list of all dataset keys which have not been deleted and are published by the given gbif publisher key.
   */
  Set<Integer> keysByPublisher(@Param("publisher") UUID publisher);

  /**
   * list datasets which have not been imported before, ordered by date created.
   * Includes private datasets.
   *
   * @param limit maximum of datasets to return
   */
  List<DatasetAttempt> listNeverImported(@Param("limit") int limit);

  /**
   * list datasets which have already been imported before, but need a refresh. The dataset.importFrequency is respected for rescheduling an
   * already imported dataset.
   * Includes private datasets.
   *
   * @param limit maximum of datasets to return
   * @param defaultFrequency number in days to between import attempts when no explicit frequency is configured
   */
  List<DatasetAttempt> listToBeImported(@Param("defaultFrequency") int defaultFrequency, @Param("limit") int limit);

  class DatasetAttempt {
    private int key;
    private String alias;
    private String title;
    private boolean failed;
    private LocalDateTime lastImportAttempt;

    public int getKey() {
      return key;
    }

    public void setKey(int key) {
      this.key = key;
    }

    public String getAlias() {
      return alias;
    }

    public void setAlias(String alias) {
      this.alias = alias;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public boolean isFailed() {
      return failed;
    }

    public void setFailed(boolean failed) {
      this.failed = failed;
    }

    public LocalDateTime getLastImportAttempt() {
      return lastImportAttempt;
    }

    public void setLastImportAttempt(LocalDateTime lastImportAttempt) {
      this.lastImportAttempt = lastImportAttempt;
    }
  }

  /**
   * @return true if dataset exists and is not deleted
   */
  boolean exists(@Param("key") int key);

  Integer usageCount(@Param("key") int datasetKey);

  /**
   * @return true if dataset key exists and belongs to a private dataset. Can be a deleted dataset
   */
  boolean isPrivate(@Param("key") int key);

  /**
   * Looks up the dataset key of the latest release for a given project, ignoring temporary datasets
   * @param key the project key
   * @param publicOnly if true only include public releases
   * @param origin the kind of release, can be null to allow any
   * @param ignore list of dataset key to ignore as the latest release
   * @return dataset key of the latest release or null if no release exists
   */
  Integer latestRelease(@Param("key") int key,
                        @Param("public") boolean publicOnly,
                        @Nullable @Param("ignore") List<Integer> ignore,
                        @Nullable @Param("origin") DatasetOrigin origin
  );

  default Integer latestRelease(int key, boolean publicOnly, DatasetOrigin origin) {
    return latestRelease(key, publicOnly, null, origin);
  }

  /**
   * This looks up the public release just before the given one, ignoring any intermediate private releases.
   * Only previous releases with the same origin are considered!
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

  Integer getKeyByGBIF(@Param("key") UUID key);

  Dataset getByGBIF(@Param("key") UUID key);

  /**
   * Just looks up the publisher key for a dataset
   */
  UUID getPublisherKey(@Param("key") int key);

  /**
   * Lists all dataset keys of non deleted datasets that have a UUID GBIF registry key.
   */
  List<Integer> listKeysGBIF();

  List<DatasetGBIF> listGBIF();

  /**
   * @return the last import attempt or null if never attempted
   */
  Integer lastImportAttempt(@Param("key") int datasetKey);
  
  int updateLastImport(@Param("key") int key, @Param("attempt") int attempt);

  /**
   * Sets the datasets LastImportAttempt to now
   * @param key dataset key
   */
  int updateLastImportAttempt(@Param("key") int key);

  /**
   * @param limit optional limit to consider when looking for the maximum.
   *              If null the global maximum is returned, if existing the maximum key below the given limit
   */
  Integer getMaxKey(@Param("limit") Integer limit);

}
