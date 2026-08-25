package life.catalogue.db.mapper;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

/**
 * Reads the sector_import_pruned scratch table the unified job migration leaves behind.
 * It records the sector_import rows that migration deleted, so their names files - which nothing points
 * at any more once the metrics row is gone - can still be swept afterwards.
 * The table only exists between that migration and the sweep, hence {@link #tableExists()}.
 */
public interface SectorImportPrunedMapper {

  class PrunedAttempt {
    private int datasetKey;
    private int sectorKey;
    private int attempt;

    public int getDatasetKey() {
      return datasetKey;
    }

    public void setDatasetKey(int datasetKey) {
      this.datasetKey = datasetKey;
    }

    public int getSectorKey() {
      return sectorKey;
    }

    public void setSectorKey(int sectorKey) {
      this.sectorKey = sectorKey;
    }

    public int getAttempt() {
      return attempt;
    }

    public void setAttempt(int attempt) {
      this.attempt = attempt;
    }

    @Override
    public String toString() {
      return datasetKey + "/" + sectorKey + "#" + attempt;
    }
  }

  /**
   * @return true if the migration scratch table is still around. Everything else here requires it.
   */
  boolean tableExists();

  int count();

  /**
   * @param after keyset cursor, null to start at the beginning
   */
  List<PrunedAttempt> list(@Param("after") @Nullable PrunedAttempt after, @Param("limit") int limit);

  /**
   * Removes every record up to and including the given one in (dataset_key, sector_key, attempt) order,
   * i.e. exactly the page that was just swept.
   */
  int deleteUpTo(@Param("last") PrunedAttempt last);
}
