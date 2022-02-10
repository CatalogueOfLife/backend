package life.catalogue.common.tax;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

/**
 *
 */
public class RankUtils {
  private static List<Rank> LINNEAN_RANKS_REVERSE = Lists.reverse(Rank.LINNEAN_RANKS);
  public static Map<Rank, ColdpTerm> RANK2COLDP = Map.ofEntries(
    Map.entry(Rank.KINGDOM, ColdpTerm.kingdom),
    Map.entry(Rank.PHYLUM, ColdpTerm.phylum),
    Map.entry(Rank.SUBPHYLUM, ColdpTerm.subphylum),
    Map.entry(Rank.CLASS, ColdpTerm.class_),
    Map.entry(Rank.SUBCLASS, ColdpTerm.subclass),
    Map.entry(Rank.ORDER, ColdpTerm.order),
    Map.entry(Rank.SUBORDER, ColdpTerm.suborder),
    Map.entry(Rank.SUPERFAMILY, ColdpTerm.superfamily),
    Map.entry(Rank.FAMILY, ColdpTerm.family),
    Map.entry(Rank.SUBFAMILY, ColdpTerm.subfamily),
    Map.entry(Rank.TRIBE, ColdpTerm.tribe),
    Map.entry(Rank.SUBTRIBE, ColdpTerm.subtribe),
    Map.entry(Rank.GENUS, ColdpTerm.genus),
    Map.entry(Rank.SUBGENUS, ColdpTerm.subgenus),
    Map.entry(Rank.SECTION, ColdpTerm.section)
  );

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
   * Returns true if r1 is a higher rank than r2 and none of the 2 ranks are uncomparable or ambiguous between codes.
   */
  public static boolean higherThanCodeAgnostic(Rank r1, Rank r2) {
    return (!r1.isUncomparable() && !r2.isUncomparable() && r1.higherThan(r2) && !r1.isAmbiguous() && !r2.isAmbiguous());
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

  public static Rank lowestRank(Collection<Rank> ranks) {
    if (ranks != null && !ranks.isEmpty()) {
      LinkedList<Rank> rs = new LinkedList<>(ranks);
      Collections.sort(rs);
      return rs.getLast();
    }
    return null;
  }

  public static ColdpTerm toColTerm(Rank rank) {
    if (rank != null) {
      return RANK2COLDP.getOrDefault(rank, null);
    }
    return null;
  }

}
