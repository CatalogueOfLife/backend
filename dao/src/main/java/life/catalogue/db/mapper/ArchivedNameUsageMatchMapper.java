package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.DatasetProcessable;

import org.apache.ibatis.annotations.Param;

/**
 * WARNING !!!
 * Only SectorProcessable.deleteBySector is implemented, no other methods of SectorProcessable!!!
 * We also store NameMatches for archived names of a project, as those names belong to the project and have unique ids not used any longer in the project itself.
 * Processing a project dataset therefore includes the matches of these archived names.
 */
public interface ArchivedNameUsageMatchMapper extends MatchMapper, DatasetProcessable<NameMatch> {

  /**
   * Copy new match records from the release to the archive
   * for all archive usages that do not yet have a match.
   * @param projectKey
   * @param releaseKey
   * @return number of new archived match records
   */
  int createMissingMatches(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Copies all matches for all archive records from the first release.
   * Note that this requires all original releases to still be present.
   * It can only be used when rebuilding archive!
   * @return number of new archived match records
   */
  int createAllMatches();

  /**
   * @param key the name key
   */
  default void create(DSID<String> key, Integer nidx, MatchType type) {
    create(key, null, nidx, type);
  }
}
