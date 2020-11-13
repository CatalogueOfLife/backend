package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.CopyDataset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import javax.annotation.Nullable;

public interface NameMatchMapper extends CopyDataset {

  int deleteByDataset(@Param("datasetKey") int datasetKey);

  int deleteBySector(@Param("key") DSID<Integer> sectorKey);

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

  void update(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId,
                   @Param("indexId") Integer indexId,
                   @Param("type") MatchType type
  );

  void create(@Param("datasetKey") int datasetKey, @Param("sectorKey") Integer sectorKey, @Param("nameId") String nameId,
                   @Param("indexId") Integer indexId,
                   @Param("type") MatchType type
  );

  int count(@Param("nidx") int nidx, @Nullable @Param("datasetKey") Integer datasetKey);

  void truncate();

}
