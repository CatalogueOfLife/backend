package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameMatch;
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
   * Re-points existing archive matches at the names index entry the release has, for archive records whose name has
   * changed since - the companion to refreshing the archived usages themselves. The nidx is the bucket the release id
   * mapping groups candidates by, so an archive record whose name was corrected has to move bucket with it or it can
   * never be found again.
   *
   * Only rows whose match actually differs are touched. A name that lost its match altogether keeps the one it has:
   * name_match never stores a null index_id, so there is nothing to re-point it to.
   *
   * @param projectKey
   * @param releaseKey
   * @return number of re-pointed archived match records
   */
  int refreshMatches(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey);

  /**
   * Copies all matches for all archive records from the last release each id appeared in, which is the version the
   * archived name itself is kept at.
   * Note that this requires all original releases to still be present.
   * It can only be used when rebuilding archive!
   * @return number of new archived match records
   */
  int createAllMatches();

  /**
   * @param key the name key
   */
  default void create(DSID<String> key, Integer nidx) {
    create(key, null, nidx);
  }
}
