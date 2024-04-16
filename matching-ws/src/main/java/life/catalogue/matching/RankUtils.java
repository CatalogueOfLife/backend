package life.catalogue.matching;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.regex.Pattern;
import org.gbif.nameparser.api.Rank;

public class RankUtils {

  private static final Pattern PREFIX =
      Pattern.compile("^(SUPER|SUB(?:TER)?|INFRA|GIGA|MAGN|GRAND|MIR|NAN|HYPO|MIN|PARV|MEGA|EPI)");
  private static final List<Rank> LINNEAN_RANKS_REVERSE = Lists.reverse(Rank.LINNEAN_RANKS);

  public static Rank nextHigherLinneanRank(Rank rank) {
    for (Rank r : LINNEAN_RANKS_REVERSE) {
      if (r.ordinal() < rank.ordinal()) {
        return r;
      }
    }
    return null;
  }
}
