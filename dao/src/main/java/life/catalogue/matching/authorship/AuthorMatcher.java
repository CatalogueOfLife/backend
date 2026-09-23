package life.catalogue.matching.authorship;

import life.catalogue.matching.Equality;

/**
 * Decides whether two author teams name the same authors. {@link AuthorComparator} does everything around it: it
 * compares the years, lines up combination against basionym authorship and selects which authors of a team count
 * under which code. It never hands over an empty team.
 */
public interface AuthorMatcher {

  /**
   * How strict a comparison is, as the comparator needs it.
   */
  enum Mode {
    /** the years agree, or at least one is missing */
    LAX(4, 4, 90),
    /** both years are given and differ, but within the tolerance: only close author strings still count */
    YEAR_CONFLICT(12, 4, 99),
    /** {@link AuthorComparator#compareStrict}: every author is looked up in the author map, no fuzzy surnames */
    STRICT(4, Integer.MAX_VALUE, 100);

    /** the length of a common start that makes two surnames match */
    public final int minCommonStart;
    /** authors shorter than this are looked up in the author map before they are compared */
    public final int lookupShorterThan;
    /** the Jaro-Winkler similarity in percent above which two surnames match */
    public final int jaroDistance;

    Mode(int minCommonStart, int lookupShorterThan, int jaroDistance) {
      this.minCommonStart = minCommonStart;
      this.lookupShorterThan = lookupShorterThan;
      this.jaroDistance = jaroDistance;
    }
  }

  /**
   * @param t1 a team of at least one author
   * @param t2 a team of at least one author
   */
  Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode);
}
