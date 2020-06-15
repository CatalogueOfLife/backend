package life.catalogue.common.tax;

import com.google.common.collect.Lists;
import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
public class RankUtils {
  private static List<Rank> LINNEAN_RANKS_REVERSE = Lists.reverse(Rank.LINNEAN_RANKS);

  /**
   * @return a list of all ranks above or equal the given minimum rank.
   */
  public static List<Rank> minRanks(Rank rank) {
    return Arrays.stream(Rank.values()).filter(
      r -> r.ordinal() <= rank.ordinal()
    ).collect(Collectors.toList());
  }

  /**
   * @return a list of all ranks below or equal the given maximum rank.
   */
  public static List<Rank> maxRanks(Rank rank) {
    return Arrays.stream(Rank.values()).filter(
      r -> r.ordinal() >= rank.ordinal()
    ).collect(Collectors.toList());
  }

  /**
   * The ranks between the given minimum and maximum
   * @param inclusive if true also include the given min and max ranks
   */
  public static Set<Rank> between(Rank min, Rank max, boolean inclusive) {
    Set<Rank> ranks = new HashSet<>(RankUtils.minRanks(min));
    ranks.retainAll(RankUtils.maxRanks(max));
    if (!inclusive) {
      ranks.remove(min);
      ranks.remove(max);
    }
    return ranks;
  }

  public static Rank nextLowerLinneanRank(Rank rank) {
    for (Rank r : Rank.LINNEAN_RANKS) {
      if (r.ordinal() > rank.ordinal()) {
        return r;
      }
    }
    return null;
  }

  public static Rank nextHigherLinneanRank(Rank rank) {
    for (Rank r : LINNEAN_RANKS_REVERSE) {
      if (r.ordinal() < rank.ordinal()) {
        return r;
      }
    }
    return null;
  }
}
