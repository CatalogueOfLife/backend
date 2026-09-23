package life.catalogue.matching.authorship;

import life.catalogue.api.model.ScientificName;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorMatcher.Mode;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;

/**
 * Utility to compare scientific name authorships, i.e. the recombination and basionym author and publishing year.
 * Author strings are normalized to ASCII and then compared. As authors are often abbreviated in all kind of ways a shared common substring is accepted
 * as a positive equality.
 * If any of the names given has an empty author & year the results will always be Equality.UNKNOWN.
 * <p>
 * The class exposes two kind of compare methods. A strict one always requiring both year and author to match
 * and a more lax default comparison that only looks at years when the authors differ (as it is quite hard to compare authors)
 * <p>
 * Who an author is, is decided by an {@link AuthorMatcher}: this class keeps what is about the structure of a name - the
 * year tolerances, combination against basionym authorship and which team counts under which code. The default matcher
 * compares strings, see {@link StringAuthorMatcher}.
 */
public class AuthorComparator {
  private final AuthorMatcher matcher;

  public AuthorComparator(AuthorshipNormalizer normalizer) {
    this(new StringAuthorMatcher(normalizer));
  }

  public AuthorComparator(AuthorMatcher matcher) {
    this.matcher = matcher;
  }

  /**
   * Compares the authorteams and year of two names.
   * If given both the year and authorteam needs to match to yield an EQUAL,
   * with a small difference of 11 years being accepted.
   */
  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2) {
    return compare(a1, a2, (NomCode) null);
  }

  /**
   * @param code the nomenclatural code of the names, which selects the author map to look up abbreviations in.
   *             Unlike in {@link #compareStrict(Authorship, Authorship, NomCode, int)} it does not select the relevant
   *             author team: ex authors keep being compared, as sources leave them out all the time.
   */
  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2, @Nullable NomCode code) {
    return compare(a1, a2, code, null);
  }

  /**
   * @param group the taxonomic group of both names if known, for a matcher that knows which groups a person worked on
   */
  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2, @Nullable NomCode code, @Nullable TaxGroup group) {
    return compareAuthorships(a1, a2, new AuthorContext(code, group));
  }

  /**
   * Not an overload of compare: next to compare(a1, a2, NomCode) every call passing a literal null would be ambiguous.
   */
  private Equality compareAuthorships(@Nullable Authorship a1, @Nullable Authorship a2, AuthorContext ctx) {
    // compare year first - simpler to calculate
    var yc = new YearComparator(11, a1, a2);
    Equality result = yc.compare();
    // compare authors if it's not already different
    if (result != Equality.DIFFERENT) {
      Equality aresult;
      if (result == Equality.EQUAL || !yc.hasYears()) {
        aresult = compareAuthorteam(a1, a2, null, ctx, Mode.LAX);
      } else {
        aresult = compareAuthorteam(a1, a2, null, ctx, Mode.YEAR_CONFLICT);
        // if unknown years and author is also unknown, make this a mismatch
        if (aresult == Equality.UNKNOWN) {
          return Equality.DIFFERENT;
        }
      }
      return result.and(aresult);
    }
    return result;
  }

  /**
   * This ported over from gbif/checklistbank.
   * The {@link AuthorComparator.compare} compares years first
   * which leads to very different results compared to current GBIF API.
   *
   * Compares the authorteams and year of two names.
   * If given both the year and authorteam needs to match to yield an EQUAL,
   * with a small difference of 2 years being accepted.
   */
  public Equality compareAuthorsFirst(@Nullable Authorship a1, @Nullable Authorship a2) {
    return compareAuthorsFirst(a1, a2, null);
  }

  /**
   * @param code the nomenclatural code of the names, which selects the author map to look up abbreviations in
   */
  public Equality compareAuthorsFirst(@Nullable Authorship a1, @Nullable Authorship a2, @Nullable NomCode code) {
    // compare year first - simpler to calculate
    Equality result = compareAuthorteam(a1, a2, null, new AuthorContext(code, null), Mode.LAX);
    if (result != Equality.EQUAL) {
      // if authors are not the same we allow a positive year comparison to override it as author comparison is very difficult
      Equality yresult = new YearComparator(a1.getYear(), a2.getYear()).compare();
      if (yresult != Equality.UNKNOWN) {
        if (yresult == Equality.DIFFERENT || a1.getAuthors().isEmpty()  || a2.getAuthors().isEmpty()) {
          result = yresult;
        } else {
          // year EQUAL, i.e. very close by
          // also make sure we have at least one capital char overlap between the 2 authorships
          Set<Character> upper1 = String.join("; ", a1.getAuthors()).chars()
            .filter(Character::isUpperCase)
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
          Set<Character> upper2 = String.join("; ", a2.getAuthors()).chars()
            .filter(Character::isUpperCase)
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
          upper1.retainAll(upper2);
          if (!upper1.isEmpty()) {
            result = yresult;
          }
        }
      }
    }
    return result;
  }

  /**
   * Does a comparison of recombination and basionym authorship using the author compare method once for the recombination authorship and once for the basionym.
   */
  public Equality compare(ScientificName n1, ScientificName n2) {
    return compare(n1, n2, (TaxGroup) null);
  }

  /**
   * @param group the taxonomic group of both names if known, for a matcher that knows which groups a person worked on
   */
  public Equality compare(ScientificName n1, ScientificName n2, @Nullable TaxGroup group) {
    final AuthorContext ctx = new AuthorContext(ObjectUtils.coalesce(n1.getCode(), n2.getCode()), group);
    return compare(n1, n2, (a1, a2) -> compareAuthorships(a1, a2, ctx));
  }

  /**
   * Alternative to {@link #compare(ScientificName, ScientificName)} that places less weight on the year in the authorship comparison.
   * This is used by GBIF matching algorithm.
   */
  public Equality compareAuthorsFirst(ScientificName n1, ScientificName n2) {
    final NomCode code = ObjectUtils.coalesce(n1.getCode(), n2.getCode());
    return compare(n1, n2, (a1, a2) -> compareAuthorsFirst(a1, a2, code));
  }

  private Equality compare(
    ScientificName n1,
    ScientificName n2,
    BiFunction<Authorship, Authorship, Equality> comparator
  ) {
    Equality recomb = comparator.apply(n1.getCombinationAuthorship(), n2.getCombinationAuthorship());
    if (recomb != Equality.UNKNOWN) {
      return recomb;
    }

    Equality original = comparator.apply(n1.getBasionymAuthorship(), n2.getBasionymAuthorship());
    if (original == Equality.UNKNOWN) {
      Equality across = Equality.UNKNOWN;
      if (n1.getCombinationAuthorship().isEmpty()) {
        across = comparator.apply(n1.getBasionymAuthorship(), n2.getCombinationAuthorship());
      } else if (n1.getBasionymAuthorship().isEmpty()) {
        across = comparator.apply(n1.getCombinationAuthorship(), n2.getBasionymAuthorship());
      }
      return across == Equality.EQUAL ? Equality.EQUAL : Equality.UNKNOWN;
    }

    return recomb.and(original);
  }

  public boolean compareStrict(ScientificName n1, ScientificName n2) {
    var code = ObjectUtils.coalesce(n1.getCode(), n2.getCode());
    var a1 = n1.getBasionymAuthorship().isEmpty() && n2.getBasionymAuthorship().isEmpty()
      || compareStrict(n1.getBasionymAuthorship(), n2.getBasionymAuthorship(), code, 0);
    var a2 = n1.getCombinationAuthorship().isEmpty() && n2.getCombinationAuthorship().isEmpty()
      || compareStrict(n1.getCombinationAuthorship(), n2.getCombinationAuthorship(), code, 0);
    return a1 && a2;
  }

  /**
   * Compares two sets of author & year for equality.
   * This is more strict than the normal compare method and requires both authors and year to match.
   * A missing year will match any year, only different years causes the comparison to fail.
   * It also ignores the ex authors in the comparison, making use of the nomenclatural code given
   * to identify the relevant authorteam for comparison - which is the later in botany and the first team in zoology.
   * If the code is not known it will default to the botanical ordering which is much more frequent.
   *
   * Author matching is still done fuzzily
   *
   * @param yearDifferenceAllowed number of years allowed to differ to still be considered a match
   *
   * @return true if both sets match
   */
  public boolean compareStrict(Authorship a1, Authorship a2, NomCode code, int yearDifferenceAllowed) {
    // strictly compare authors first
    Equality result = compareAuthorteam(a1, a2, code, new AuthorContext(code, null), Mode.STRICT);
    if (result != Equality.EQUAL) {
      return false;
    }
    // now also compare the year
    if (a1.getYear() == null || a2.getYear() == null) {
      return true;
    }
    return Equality.DIFFERENT != new YearComparator(yearDifferenceAllowed, a1.getYear(), a2.getYear()).compare();
  }

  /**
   * @param code the code determines which ex author to use and which author map. If null both authorteams are used for matching
   */
  @VisibleForTesting
  Equality compareAuthorteam(Authorship a1, Authorship a2, NomCode code) {
    return compareAuthorteam(a1, a2, code, new AuthorContext(code, null), Mode.LAX);
  }

  /**
   * Selects and normalizes both teams and hands them to the matcher, never an empty one.
   *
   * @param teamCode determines which of authors and ex authors are compared. If null both are
   */
  private Equality compareAuthorteam(@Nullable Authorship a1, @Nullable Authorship a2, @Nullable NomCode teamCode,
                                     AuthorContext ctx, Mode mode) {
    // convert to all lower case, ascii only, no punctuation but commas seperating authors and normed whitespace
    List<String> team1 = AuthorshipNormalizer.normalize(a1, teamCode);
    List<String> team2 = AuthorshipNormalizer.normalize(a2, teamCode);
    if (team1.isEmpty() || team2.isEmpty()) {
      return Equality.UNKNOWN;
    }
    return matcher.compareTeams(new AuthorTeam(team1, a1.getYear()), new AuthorTeam(team2, a2.getYear()), ctx, mode);
  }

}
