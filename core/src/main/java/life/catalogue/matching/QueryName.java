package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.List;
import java.util.Objects;

/**
 * The name of an external match query as the usage matcher expects it. On top of the interpreted name it keeps what
 * only the name as written can tell: whether an infraspecific name showed its rank by a marker.
 * Interpretation rebuilds a name from its rank, so Anas platyrhynchos domesticus sent as a variety becomes
 * Anas platyrhynchos var. domesticus and looks like a name that states its rank.
 */
public class QueryName extends SimpleNameClassified<SimpleNameCached> {
  private final boolean unmarkedRank;

  /**
   * @param unmarkedRank true if the rank was given apart from an infraspecific name that shows no rank marker
   */
  public QueryName(SimpleNameCached name, List<SimpleNameCached> classification, boolean unmarkedRank) {
    super(name, classification);
    this.unmarkedRank = unmarkedRank;
  }

  /**
   * @return true if the rank was given apart from an infraspecific name that shows no rank marker,
   *         so it is only what the caller believes the rank to be
   */
  public boolean isUnmarkedRank() {
    return unmarkedRank;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QueryName)) return false;
    if (!super.equals(o)) return false;
    return unmarkedRank == ((QueryName) o).unmarkedRank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), unmarkedRank);
  }
}
