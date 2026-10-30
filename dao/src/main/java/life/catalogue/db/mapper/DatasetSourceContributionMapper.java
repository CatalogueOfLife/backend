package life.catalogue.db.mapper;

import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.db.type2.StringCount;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

/**
 * What the source datasets of a release gave to records they do not own, one row per source.
 * Recorded once from verbatim_source_secondary when a release finishes, as counting those rows on every read of the
 * source metrics is too slow for a page that requests them per source.
 */
public interface DatasetSourceContributionMapper {

  class Contribution {
    private int enriched;
    private Map<InfoGroup, Integer> byInfo = new HashMap<>();

    public int getEnriched() {
      return enriched;
    }

    public void setEnriched(int enriched) {
      this.enriched = enriched;
    }

    public Map<InfoGroup, Integer> getByInfo() {
      return byInfo;
    }

    public void setByInfo(Map<InfoGroup, Integer> byInfo) {
      this.byInfo = byInfo;
    }
  }

  /**
   * Counts the secondary sources of a release and stores them.
   * @return number of source datasets recorded
   */
  int createForRelease(@Param("datasetKey") int datasetKey);

  /**
   * @return the contribution of a single source or null if it gave nothing to the release
   */
  @Nullable
  Contribution get(@Param("datasetKey") int datasetKey, @Param("sourceKey") int sourceKey);

  /**
   * @return number of records enriched by all sources of a publisher. A record credited to two of them counts twice.
   */
  int sumEnrichedByPublisher(@Param("datasetKey") int datasetKey, @Param("publisherKey") UUID publisherKey);

  /**
   * @return the credits of all sources of a publisher per info group
   */
  List<StringCount> countByInfoByPublisher(@Param("datasetKey") int datasetKey, @Param("publisherKey") UUID publisherKey);

  int deleteByRelease(@Param("datasetKey") int datasetKey);
}
