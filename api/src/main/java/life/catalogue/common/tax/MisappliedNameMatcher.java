package life.catalogue.common.tax;

import life.catalogue.api.model.ParsedNameUsage;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Utility class that detects misapplied names mostly based on their taxonomic remarks.
 */
public class MisappliedNameMatcher {
  private static final Pattern MIS_PATTERN = Pattern.compile("\\b(auct|sensu|non\\b|nec\\b)(?![ .]?(lat|str))", Pattern.CASE_INSENSITIVE);
  // a misspelt name like "auct. misspelling" or "auct. lapsus" is a nomenclatural matter, no misapplication
  private static final Pattern MISSPELLING = Pattern.compile("\\b(misspel\\w*|lapsus|err(?:or)?\\b|orth\\b)", Pattern.CASE_INSENSITIVE);
  
  /**
   * Detect a misapplied name based on taxonomic remarks and rank.
   * Rank must be a binomial at least.
   * The taxnomic remarks must start with either of:
   * auct. [Author name]
   * sensu [Author name]
   * (sensu) auctorum
   * (sensu) auct.
   * (sensu) auct. non [Author name]
   * (sensu) auct. nec [Author name]
   * but never a misspelling like auct. misspelling, auct. lapsus or auct. err.
   *
   * @return true if a misapplied name format was detected
   */
  public static boolean isMisappliedName(ParsedNameUsage nat) {
    if (nat != null && nat.getName() != null && nat.getName().getRank().isSpeciesOrBelow()) {
      return isMisappliedNote(nat.getTaxonomicNote());
    }
    return false;
  }

  /**
   * The rule of {@link #isMisappliedName(ParsedNameUsage)} for the taxonomic note alone, e.g. a name phrase: it does
   * not look at the rank.
   * @return true if the note marks a misapplied name
   */
  public static boolean isMisappliedNote(@Nullable String note) {
    return note != null && MIS_PATTERN.matcher(note).find() && !MISSPELLING.matcher(note).find();
  }

}
