package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameMatch;
import life.catalogue.db.DatasetProcessable;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * WARNING !!!
 * Only SectorProcessable.deleteBySector is implemented, no other methods of SectorProcessable!!!
 * We also store NameMatches for archived names of a project, as those names belong to the project and have unique ids not used any longer in the project itself.
 * Processing a project dataset therefore includes the matches of these archived names.
 */
public interface ArchivedNameUsageMatchMapper extends MatchMapper, DatasetProcessable<NameMatch> {

  /**
   * Copies the names index matches of a release's usages into the archive, only where they differ. If insertedOnly is
   * false (the release supplies versions) this touches every archive record no blocking release holds; if true (the
   * release does not supply versions, e.g. ignored or a lower ranked duplicate generation) it is restricted to the
   * records the release itself just inserted, so a non supplying release never re-points the match of a record whose
   * version, and therefore whose match, another release owns. See NameUsageArchiver.
   * @return number of inserted or changed archive matches
   */
  int copyReleaseMatches(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                         @Param("blocking") List<Integer> blocking, @Param("insertedOnly") boolean insertedOnly);

  /**
   * Removes the archive match of the release usages whose name has no match in the release: an empty match is never
   * stored. Scoped the same way as copyReleaseMatches - see there for what insertedOnly restricts.
   * @return number of removed archive matches
   */
  int deleteUnmatchedReleaseMatches(@Param("projectKey") int projectKey, @Param("releaseKey") int releaseKey,
                                    @Param("blocking") List<Integer> blocking, @Param("insertedOnly") boolean insertedOnly);

  /**
   * @param key the name key
   */
  default void create(DSID<String> key, Integer nidx) {
    create(key, null, nidx);
  }
}
