package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.TempNameUsageRelated;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * WARNING !!!
 * Only SectorProcessable.deleteBySector is implemented, no other methods of SectorProcessable!!!
 * We also store NameMatches for archived names of a project, as those names belong to the project and have unique ids not used any longer in the project itself.
 * Processing a manage dataset therefore includes the matches of these archived names.
 */
public interface NameMatchMapper extends CopyDataset, DatasetProcessable<NameMatch>, SectorProcessable<Integer>, TempNameUsageRelated {

  /**
   * @return true if at least one name match for the given dataset exists
   */
  boolean exists(@Param("datasetKey") int datasetKey);

  /**
   * Removes all matches that have no related name record for the given dataset.
   */
  int deleteOrphans(@Param("datasetKey") int datasetKey);

  /**
   * Lists all distinct index ids from the name match table.
   */
  Cursor<Integer> processIndexIds(@Param("datasetKey") int datasetKey,
                                  @Param("sectorKey") @Nullable Integer sectorKey);

  /**
   * Retrieve a single name match for a given name key.
   * Alternatives will always be null as they are not stored.
   */
  NameMatch get(@Param("key") DSID<String> key);

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

  /**
   * Count all matches for the given dataset
   */
  int count(@Param("datasetKey") int datasetKey);

  int countNidx(@Param("nidx") int nidx, @Nullable @Param("datasetKey") Integer datasetKey);

  void truncate();

}
