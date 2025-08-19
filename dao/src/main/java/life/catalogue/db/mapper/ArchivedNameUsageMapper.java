package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;

import java.util.List;

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
  Cursor<ArchivedSimpleNameWithNidx> processArchivedUsages(@Param("datasetKey") int datasetKey);

  /**
   * Process all archived name usages as Name instances with names index match infos.
   * Not the "Name" key is exceptionally a usage key!
   * @param onlyMissingMatches if true only names without an existing matching record will be re-matched
   */
  Cursor<Name> processArchivedNames(@Nullable @Param("datasetKey") Integer datasetKey,
                                    @Param("onlyMissing") boolean onlyMissingMatches
  );

  /**
   * Adds the release_key to the list of existing release keys
   * for all archived usages for a given project that still exist in the given release (based on the usage ID alone)
   * @param projectKey
   * @param releaseKey
   * @return number of updated archive records
   */
  int addReleaseKey(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Create new archive records for all usages in the given release
   * which not yet exist in the archive (based on the usage ID alone)
   * @param projectKey
   * @param releaseKey
   * @return number of new archive records
   */
  int createMissingUsages(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

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

  class ArchivedSimpleNameWithNidx extends SimpleNameWithNidx {
    private int[] releaseKeys; // release datasetKey

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

  }
}
