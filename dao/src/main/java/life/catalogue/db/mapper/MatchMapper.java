package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface MatchMapper {

  /**
   * Removes all matches that have no related name record for the given dataset.
   */
  int deleteOrphans(@Param("datasetKey") int datasetKey);

  /**
   * Lists all distinct index ids from the name match table.
   */
  Cursor<Integer> processIndexIds(@Param("datasetKey") int datasetKey);

  /**
   * Retrieve a single name match for a given name key.
   * Alternatives will always be null as they are not stored.
   */
  NameMatch get(@Param("key") DSID<String> key);

  /**
   * @param key the name key
   * @return the number of records updated
   */
  int update(@Param("key") DSID<String> key,
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
   * @return the number of records deleted
   */
  int delete(@Param("key") DSID<String> key);

  /**
   * Count all matches for the given dataset
   */
  int countByDataset(@Param("datasetKey") int datasetKey);

  int countNidx(@Param("nidx") int nidx, @Nullable @Param("datasetKey") Integer datasetKey);

  void truncate();
}
