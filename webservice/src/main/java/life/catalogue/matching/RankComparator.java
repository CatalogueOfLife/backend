package life.catalogue.matching;

import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;

public class RankComparator {

  public static class RankRange {
    Rank rank;
    Rank max;
    Rank min;
  }

  /**
   * Compares two ranks. Returns UNKNOWN for possible, but uncertain matches.
   */
  public static Equality compare(@Nullable Rank r1, @Nullable Rank r2) {
    r1 = r1 == null ? Rank.UNRANKED : r1;
    r2 = r2 == null ? Rank.UNRANKED : r2;
    if (r1 == r2) {
      return Equality.EQUAL;
    }
    if (r1 == Rank.OTHER || r2 == Rank.OTHER) {
      return Equality.DIFFERENT;
    }
    if (r1 == Rank.UNRANKED || r2 == Rank.UNRANKED) {
      return Equality.UNKNOWN;
    }
    if (r1.isUncomparable()) {
      return compareVagueRanks(r1, r2);
    } else if (r2.isUncomparable()) {
      return compareVagueRanks(r2, r1);
    }
    return Equality.DIFFERENT;
  }

  public static Equality compareVagueRanks(Rank vague, Rank r2) {
    switch (vague) {
      case SUPRAGENERIC_NAME:
        if (r2.isSuprageneric()) {
          return Equality.UNKNOWN;
        }
        break;
      case INFRAGENERIC_NAME:
        if (r2.isInfragenericStrictly()) {
          return Equality.UNKNOWN;
        }
        break;
      case INFRASPECIFIC_NAME:
        if (r2.isInfraspecific()) {
          return Equality.UNKNOWN;
        }
        break;
      case INFRASUBSPECIFIC_NAME:
        if (r2.isInfrasubspecific()) {
          return Equality.UNKNOWN;
        }
        break;
      case UNRANKED:
        return Equality.UNKNOWN;
      default: // incl OTHER
        return Equality.DIFFERENT;
    }
    return Equality.DIFFERENT;
  }
}
