package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.SectorProcessable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import javax.annotation.Nullable;

/**
 * WARNING !!!
 * Only SectorProcessable.deleteBySector is implemented, no other methods of SectorProcessable!!!
 */
public interface NameMatchMapper extends CopyDataset, SectorProcessable<Integer> {

  /**
   * @return true if at least one name match for the given dataset exists
   */
  boolean exists(@Param("datasetKey") int datasetKey);

  int deleteByDataset(@Param("datasetKey") int datasetKey);

  /**
   * Removes all matches that have no related name record for the given dataset and optionally sector.
   */
  int deleteOrphaned(@Param("datasetKey") int datasetKey,
                     @Nullable @Param("sectorKey") Integer sectorKey);

  /**
   * Lists all distinct index ids from the name match table.
   */
  Cursor<Integer> processIndexIds(@Param("datasetKey") int datasetKey,
                         @Nullable @Param("sectorKey") Integer sectorKey);

  /**
   * @param key the name key
   */
  void update(@Param("key") DSID<String> key,
              @Param("nidx") Integer nidx,
              @Param("type") MatchType type
  );

  /**
   * @param key the name key
   */
  void create(@Param("key") DSID<String> key,
              @Param("sectorKey") Integer sectorKey,
              @Param("nidx") Integer nidx,
              @Param("type") MatchType type
  );

  /**
   * @param key the name key
   */
  void delete(@Param("key") DSID<String> key);

  int count(@Param("nidx") int nidx, @Nullable @Param("datasetKey") Integer datasetKey);

  void truncate();

}
