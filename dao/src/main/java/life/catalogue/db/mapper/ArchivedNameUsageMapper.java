package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetProcessable;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Mapper for archived name usages of a project. Dataset keys are expected to always be MANAGED, never releases.
 */
public interface ArchivedNameUsageMapper extends CRUD<DSID<String>, ArchivedNameUsage>, DatasetProcessable<ArchivedNameUsage> {

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
   */
  Cursor<Name> processArchivedNames(@Param("datasetKey") int datasetKey);


  class ArchivedSimpleNameWithNidx extends SimpleNameWithNidx {
    private int lastReleaseKey; // release datasetKey

    public int getLastReleaseKey() {
      return lastReleaseKey;
    }

    public void setLastReleaseKey(int lastReleaseKey) {
      this.lastReleaseKey = lastReleaseKey;
    }
  }
}
