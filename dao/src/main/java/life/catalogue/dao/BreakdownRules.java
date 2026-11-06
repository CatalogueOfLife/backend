package life.catalogue.dao;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Decides how a taxon's subtree is charted: at which rank the nodes are counted and which one or two ring ranks the
 * tree is built of. These used to live in the clients, which had to fetch the rank counts first to take them.
 */
public final class BreakdownRules {
  /** A second ring with more members than this is not drawn. */
  public static final int MAX_GRAND_CHILDREN = 1000;

  private BreakdownRules() {
  }

  /**
   * Species, unless genera outnumber them more than twofold - IRMNG's Arthropoda has 126k genera and 2 species, so a
   * species pie is useless there - then genus. Null when there is neither.
   *
   * @param counts accepted taxa of the subtree by rank
   */
  @Nullable
  public static Rank countRank(Map<Rank, Integer> counts) {
    int species = counts.getOrDefault(Rank.SPECIES, 0);
    int genera = counts.getOrDefault(Rank.GENUS, 0);
    if (genera > 2 * species) {
      return Rank.GENUS;
    }
    return species > 0 ? Rank.SPECIES : null;
  }

  /**
   * The ring ranks of the tree below a taxon: the first Linnean rank below it that has members, and the next one with
   * members as a second ring unless that rank is the count rank or below it, in which case every node would just count
   * itself, or it has more members than {@link #MAX_GRAND_CHILDREN}. The first ring may be the count rank itself, an
   * order holding nothing but species is charted as its species.
   *
   * @param taxonRank the rank of the charted taxon, a concrete one
   * @param counts    accepted taxa of the subtree by rank
   * @param countRank the rank the nodes are counted at, null when there is nothing to count
   * @return one or two ranks, none when there is nothing to chart
   */
  public static List<Rank> ringRanks(Rank taxonRank, Map<Rank, Integer> counts, @Nullable Rank countRank) {
    if (countRank == null) {
      return List.of();
    }
    Rank child = null;
    Rank rank = RankUtils.nextLowerLinneanRank(taxonRank);
    while (rank != null) {
      int count = counts.getOrDefault(rank, 0);
      if (count > 0) {
        if (child == null) {
          child = rank;
          if (rank.lowerOrEqualsTo(countRank)) {
            break;
          }
        } else if (rank.lowerOrEqualsTo(countRank) || count > MAX_GRAND_CHILDREN) {
          break;
        } else {
          return List.of(child, rank);
        }
      }
      rank = RankUtils.nextLowerLinneanRank(rank);
    }
    return child == null ? List.of() : List.of(child);
  }
}
