package life.catalogue.matching;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gbif.api.vocabulary.Rank;

public class RankUtils {
  private static final Pattern PREFIX =
      Pattern.compile("^(SUPER|SUB(?:TER)?|INFRA|GIGA|MAGN|GRAND|MIR|NAN|HYPO|MIN|PARV|MEGA|EPI)");
  private static List<Rank> LINNEAN_RANKS_REVERSE = Lists.reverse(Rank.LINNEAN_RANKS);

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
