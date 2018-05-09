package org.col.common.tax;

import java.util.regex.Pattern;

import org.col.api.model.Name;
import org.col.api.model.NameAccordingTo;

/**
 * Utility class that detects misapplied names mostly based on their taxonomic remarks.
 */
public class MisappliedNameMatcher {
  private static final Pattern MIS_PATTERN = Pattern.compile("\\b(auct|sensu|non\\b|nec\\b)(?![ .]?(lat|str))", Pattern.CASE_INSENSITIVE);
  /**
   * Detect a misapplied name based on taxonomic remarks and rank.
   * Rank must be a binomial at least.
   * The taxnomic remarks must start with either of:
   *  auct. [Author name]
   *  sensu [Author name]
   *  (sensu) auctorum
   *  (sensu) auct.
   *  (sensu) auct. non [Author name]
   *  (sensu) auct. nec [Author name]
   *
   * @return true if a misapplied name format was detected
   */
  public static boolean isMisappliedName(NameAccordingTo nat) {
    if (nat != null && nat.getName() != null && nat.getAccordingTo() != null && nat.getName().getRank().isSpeciesAggregateOrBelow()) {
      return MIS_PATTERN.matcher(nat.getAccordingTo()).find();
    }
    return false;
  }

}
