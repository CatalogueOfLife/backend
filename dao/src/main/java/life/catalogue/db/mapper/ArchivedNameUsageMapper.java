package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Mapper for archived name usages of a project. Dataset keys are expected to always be PROJECT, never releases.
 */
public interface ArchivedNameUsageMapper extends Create<ArchivedNameUsage>, DatasetProcessable<ArchivedNameUsage> {

  ArchivedNameUsage get(@Param("key") DSID<String> key);

  /**
   * @return number of archived usages for a given project.
   */
  int count(@Param("datasetKey") int datasetKey);

  /**
   * List all project keys that have some archived names.
   */
  List<Integer> listProjects();

  /**
   * Iterate over all archived names ordered by their canonical names index id.
   */
  Cursor<ArchivedSimpleName> processArchivedUsages(@Param("datasetKey") int datasetKey);

  /**
   * Process all archived name usages as Name instances with names index match infos.
   * Not the "Name" key is exceptionally a usage key!
   * @param datasetKey the project key to process releases from. If null processes all archive records!
   * @param onlyMissingMatches if true only names without an existing matching record will be re-matched
   */
  Cursor<Name> processArchivedNames(@Nullable @Param("datasetKey") Integer datasetKey,
                                    @Param("onlyMissing") boolean onlyMissingMatches
  );

  /**
   * Inserts the archive records missing for the usages of a release, with empty release keys: addReleaseKey adds the
   * key last. Safe to run twice, also at the same time.
   * @return number of inserted archive records
   */
  int createMissingUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Rewrites the archived version of the usages of a release wherever any archived column differs, skipping records
   * that carry one of the blocking release keys, i.e. whose version a higher ranked release holds. See ReleaseRanking.
   * @return number of rewritten archive records
   */
  int updateExistingUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                           @Param("blocking") List<Integer> blocking);

  /**
   * Adds the release key, keeping the array sorted, to every archive record of a usage of the release that lacks it.
   * The per release step runs this last, so a release key present in the archive means the release was archived completely.
   * @return number of archive records the key was added to
   */
  int addReleaseKey(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Sorts and de-duplicates the release keys of every archive record of a project.
   * @return number of changed archive records
   */
  int tidyReleaseKeys(@Param("projectKey") int projectKey);

  /**
   * Dry run counterpart of createMissingUsages.
   */
  int countMissingUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Dry run counterpart of updateExistingUsages.
   * @param renamedOnly if true only counts records whose scientific name would change
   */
  int countOutdatedUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                          @Param("blocking") List<Integer> blocking, @Param("renamedOnly") boolean renamedOnly);

  /**
   * Dry run counterpart of addReleaseKey, counting existing archive records only.
   */
  int countMissingReleaseKeys(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * @return true if any archive record of the project carries the release key
   */
  boolean isReleaseArchived(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * @return true if the release has any name usage
   */
  boolean hasUsages(@Param("releaseKey") int releaseKey);

  /**
   * Records that an identifier a release stopped using was taken over by another one - typically because the two were
   * duplicates of the same name and the junior one was removed.
   *
   * Staged per RELEASE, not per project: a release that is never published, or is deleted again, must not leave a
   * redirect behind on an identifier that is still live. {@code NameUsageArchiver.archiveRelease} folds these into
   * {@code name_usage_archive.superseded_by} once the release goes public.
   */
  void addSuperseded(@Param("releaseKey") int releaseKey, @Param("id") String id, @Param("supersededBy") String supersededBy);

  /**
   * Streams the staged supersede pairs of one release as {id, supersededBy} maps, ordered by id.
   */
  Cursor<Map<String, Object>> processSuperseded(@Param("releaseKey") int releaseKey);

  /**
   * Clears superseded_by for every archived id the given release does have, i.e. the ones it resurrected.
   * @return number of cleared archive records
   */
  int clearSuperseded(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Copies the supersede pairs staged for the given release into the project archive.
   * @return number of updated archive records
   */
  int applySuperseded(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Drops the staged supersede pairs of a release once they have been applied to the archive.
   */
  int deleteSuperseded(@Param("releaseKey") int releaseKey);

  /**
   * Lists all name usage identifiers with the same names index key across all datasets.
   *
   * @param nidx from the names index!
   */
  List<DSID<String>> indexGroupIds(@Param("nidx") int nidx);

  /**
   * Truncate entire name usage archive table
   */
  void truncate();

  class ArchivedSimpleName extends SimpleNameCached {
    private int[] releaseKeys; // release datasetKey
    private List<SimpleName> classification; // in case of synonyms the first entry is the accepted name

    public int[] getReleaseKeys() {
      return releaseKeys;
    }

    public void setReleaseKeys(int[] releaseKeys) {
      this.releaseKeys = releaseKeys;
    }

    public int getFirstReleaseKey() {
      return releaseKeys[0];
    }

    public int getLastReleaseKey() {
      return releaseKeys[releaseKeys.length-1];
    }

    public List<SimpleName> getClassification() {
      return classification;
    }

    public void setClassification(List<SimpleName> classification) {
      this.classification = classification;
    }
  }
}
