package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.*;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.util.Collections;
import java.util.List;

/**
 * WARNING !!!
 * Only SectorProcessable.deleteBySector is implemented, no other methods of SectorProcessable!!!
 * We also store NameMatches for archived names of a project, as those names belong to the project and have unique ids not used any longer in the project itself.
 * Processing a manage dataset therefore includes the matches of these archived names.
 */
public interface NameMatchMapper extends MatchMapper, CopyDataset, NameProcessable<NameMatch>, DatasetProcessable<NameMatch>, SectorProcessable<Integer> {

  /**
   * @return true if at least one name match for the given dataset & sector exists
   */
  boolean exists(@Param("datasetKey") int datasetKey,
                 @Param("sectorKey") @Nullable Integer sectorKey);

  /**
   * Lists all distinct index ids from the name match table.
   */
  Cursor<Integer> processIndexIds(@Param("datasetKey") int datasetKey,
                                  @Param("sectorKey") @Nullable Integer sectorKey);

  @Override
  default Cursor<Integer> processIndexIds(@Param("datasetKey") int datasetKey) {
    return processIndexIds(datasetKey, null);
  }
  @Override
  default List<NameMatch> listByName(@Param("key") DSID<String> key) {
    var m = get(key);
    if (m != null) {
      return List.of(m);
    }
    return Collections.emptyList();
  }

  @Override
  default int deleteByName(@Param("key") DSID<String> key) {
    return delete(key);
  }

}
