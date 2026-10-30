package life.catalogue.dao;

import life.catalogue.api.model.ImportMetrics;
import life.catalogue.api.vocab.InfoGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metrics of a source that also tell what it gave to records it does not own, which all other metrics cannot show:
 * a merge sector that only adds page links to the names of others owns no name at all.
 * It is the opposite of {@link #getSecondarySourceByInfoCount()}, which is what the records of a source received.
 * Only known for releases, where it is recorded once when the release finishes. Null for projects.
 */
public abstract class ContributionMetrics extends ImportMetrics {
  private Integer enrichedCount;
  private Map<InfoGroup, Integer> enrichedByInfoCount = new HashMap<>();

  /**
   * @return number of distinct records the source is credited on for any kind of information
   */
  public Integer getEnrichedCount() {
    return enrichedCount;
  }

  public void setEnrichedCount(Integer enrichedCount) {
    this.enrichedCount = enrichedCount;
  }

  /**
   * @return number of records the source is credited on per kind of information
   */
  public Map<InfoGroup, Integer> getEnrichedByInfoCount() {
    return enrichedByInfoCount;
  }

  public void setEnrichedByInfoCount(Map<InfoGroup, Integer> enrichedByInfoCount) {
    this.enrichedByInfoCount = enrichedByInfoCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ContributionMetrics that = (ContributionMetrics) o;
    return Objects.equals(enrichedCount, that.enrichedCount) && Objects.equals(enrichedByInfoCount, that.enrichedByInfoCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), enrichedCount, enrichedByInfoCount);
  }
}
