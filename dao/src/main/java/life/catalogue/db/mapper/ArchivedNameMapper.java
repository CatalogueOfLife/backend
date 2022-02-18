package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.db.*;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Mapper for archived names of a project. Dataset keys are expected to always be MANAGED, never releases.
 */
public interface ArchivedNameMapper extends CRUD<DSID<String>, ArchivedNameUsage>, DatasetProcessable<ArchivedNameUsage> {

  /**
   * Iterate over all archived names ordered by their canonical names index id.
   */
  Cursor<ArchivedSimpleNameWithNidx> processNxIds(@Param("datasetKey") int datasetKey);

  Cursor<NameMapper.NameWithNidx> processDatasetWithNidx(@Param("datasetKey") int datasetKey);


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
