package life.catalogue.matching.authorship;

import life.catalogue.api.model.ScientificName;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorshipComparison.Reason;
import life.catalogue.matching.similarity.JaroWinkler;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import static life.catalogue.common.tax.AuthorshipNormalizer.Author;

/**
 * Utility to compare scientific name authorships, i.e. the recombination and basionym author and publishing year.
 * Author strings are normalized to ASCII and then compared. As authors are often abbreviated in all kind of ways a shared common substring is accepted
 * as a positive equality.
 * If any of the names given has an empty author & year the results will always be Equality.UNKNOWN.
 * <p>
 * The class exposes two kind of compare methods. A strict one always requiring both year and author to match
 * and a more lax default comparison that only looks at years when the authors differ (as it is quite hard to compare authors)
 */
public class AuthorComparator {
  private static final Logger LOG = LoggerFactory.getLogger(AuthorComparator.class);
  
  private final AuthorshipNormalizer normalizer;
  static final int MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP = 4;
  static final int MIN_JARO_SURNAME_DISTANCE = 90;
  // years an authorship may differ from one only equal by the work it was published in, X in Y against Y
  static final int IN_YEAR_DIFF_ALLOWED = 1;
  private final int minCommonSubstring;
  
  public AuthorComparator(AuthorshipNormalizer normalizer) {
    this.normalizer = normalizer;
    minCommonSubstring = 4;
  }
  
  /**
   * Compares the authorteams and year of two names.
   * If given both the year and authorteam needs to match to yield an EQUAL,
   * with a small difference of 11 years being accepted.
   */
  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2) {
    return compareWithReason(a1, a2).equality();
  }

  /**
   * Like {@link #compare(Authorship, Authorship)}, also telling why.
   */
  public AuthorshipComparison compareWithReason(@Nullable Authorship a1, @Nullable Authorship a2) {
    // compare year first - simpler to calculate
    var yc = new YearComparator(11, a1, a2);
    Equality years = yc.compare();
    if (years == Equality.DIFFERENT) {
      // the years decide, but if the authors differ as well that is the better explanation
      var authors = authorteams(a1, a2, minCommonSubstring, MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP, MIN_JARO_SURNAME_DISTANCE, null);
      return new AuthorshipComparison(Equality.DIFFERENT,
        authors.equality() == Equality.DIFFERENT ? Reason.DIFFERENT_AUTHOR : Reason.DIFFERENT_YEAR);
    }
    // compare authors as the years are not different
    AuthorshipComparison authors;
    if (years == Equality.EQUAL || !yc.hasYears()) {
      authors = authorteams(a1, a2, minCommonSubstring, MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP, MIN_JARO_SURNAME_DISTANCE, null);
    } else {
      authors = authorteams(a1, a2, minCommonSubstring * 3, MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP, 99, null);
      // if unknown years and author is also unknown, make this a mismatch
      if (authors.equality() == Equality.UNKNOWN) {
        return new AuthorshipComparison(Equality.DIFFERENT, Reason.DIFFERENT_AUTHOR);
      }
    }
    Equality eq = years.and(authors.equality());
    if (eq == Equality.EQUAL && authors.equality() == Equality.UNKNOWN) {
      // only the years could tell, e.g. an anonymous work against a named author
      return new AuthorshipComparison(eq, Reason.SAME_YEAR);
    }
    return new AuthorshipComparison(eq, authors.reason());
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
    // compare year first - simpler to calculate
    Equality result = compareAuthorteam(a1, a2, minCommonSubstring, MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP, MIN_JARO_SURNAME_DISTANCE, null);
    if (result != Equality.EQUAL) {
      // if authors are not the same we allow a positive year comparison to override it as author comparison is very difficult
      Equality yresult = new YearComparator(a1.getYear(), a2.getYear()).compare();
      if (yresult != Equality.UNKNOWN) {
        if (yresult == Equality.DIFFERENT || !a1.hasAuthors() || !a2.hasAuthors()) {
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
   *
   * The rules are the same for every nomenclatural code, see {@link #compareWithReason(ScientificName, ScientificName)}.
   * For authors cited in different roles, one with and one without brackets, it can return
   * {@link Equality#PROBABLY_EQUAL} or {@link Equality#PROBABLY_DIFFERENT}, which callers checking for
   * {@link Equality#EQUAL} or {@link Equality#DIFFERENT} read like {@link Equality#UNKNOWN}.
   */
  public Equality compare(ScientificName n1, ScientificName n2) {
    return compareWithReason(n1, n2).equality();
  }

  /**
   * Compares the authorships of two names and tells why. The rules do not depend on the nomenclatural code:
   * <ul>
   *   <li>Two combination authors decide alone: {@code (Kunth) Desv.} is {@code (Humb. & Bonpl.) Desv.}.
   *   An unbracketed author on one side may also be the combination author with the basionym author left out,
   *   {@code Suksd.} is {@code (G.S.Torr.) Suksd.}</li>
   *   <li>Otherwise the bracketed authors decide, if both sides have one.</li>
   *   <li>An unbracketed author without a bracketed one is the original author. It is the same name as the other
   *   side's bracketed author, the brackets added or lost: {@code Pascoe, 1864} is {@code (Pascoe, 1859)}. Next to a
   *   combination author it is only {@link Equality#PROBABLY_EQUAL}, {@code L.} against {@code (L.) Mill.}: both often
   *   exist, an original name and a later transfer of a basionym of the same author from another genus, like
   *   {@code Aechmea blanchetii Baker} and {@code Aechmea blanchetii (Baker) Mez}. A different author against a
   *   bracketed one only is {@link Equality#PROBABLY_DIFFERENT}:
   *   {@code Harrington, 1899} against {@code (Kirby, 1837)} is the zoological way of citing two different original
   *   authors, and only a botanical citation leaving out both the basionym and the combination author could make
   *   them the same name.</li>
   * </ul>
   */
  public AuthorshipComparison compareWithReason(ScientificName n1, ScientificName n2) {
    return compare(n1, n2, this::compareWithReason);
  }

  /**
   * Alternative to {@link #compare(ScientificName, ScientificName)} that places less weight on the year in the authorship comparison.
   * This is used by GBIF matching algorithm.
   */
  public Equality compareAuthorsFirst(ScientificName n1, ScientificName n2) {
    return compare(n1, n2, (a1, a2) -> withoutReason(compareAuthorsFirst(a1, a2))).equality();
  }

  private static AuthorshipComparison withoutReason(Equality eq) {
    return switch (eq) {
      case EQUAL -> new AuthorshipComparison(eq, Reason.SAME_AUTHOR);
      case PROBABLY_EQUAL -> new AuthorshipComparison(eq, Reason.BASIONYM_AUTHOR_CITED);
      case DIFFERENT, PROBABLY_DIFFERENT -> new AuthorshipComparison(eq, Reason.DIFFERENT_AUTHOR);
      case UNKNOWN -> AuthorshipComparison.MISSING;
    };
  }

  private static boolean isEmpty(@Nullable Authorship a) {
    return a == null || a.isEmpty();
  }

  private AuthorshipComparison compare(
    ScientificName n1,
    ScientificName n2,
    BiFunction<Authorship, Authorship, AuthorshipComparison> comparator
  ) {
    final Authorship c1 = n1.getCombinationAuthorship();
    final Authorship c2 = n2.getCombinationAuthorship();
    final Authorship b1 = n1.getBasionymAuthorship();
    final Authorship b2 = n2.getBasionymAuthorship();

    final var recomb = comparator.apply(c1, c2);
    if (recomb.equality() == Equality.EQUAL) {
      // the same combination author, whatever the basionym authors are - also when one side leaves them out
      return isEmpty(b1) != isEmpty(b2) ? new AuthorshipComparison(Equality.EQUAL, Reason.BASIONYM_OMITTED) : recomb;
    }
    if (recomb.equality() == Equality.DIFFERENT) {
      // L. against (L.) Mill.: the same original author, but often two names that both exist - an original name and
      // a later transfer of a basionym of that author from another genus - so only probably equal
      if (isEmpty(b1) && !isEmpty(b2) && comparator.apply(c1, b2).equality() == Equality.EQUAL
          || isEmpty(b2) && !isEmpty(b1) && comparator.apply(b1, c2).equality() == Equality.EQUAL) {
        return new AuthorshipComparison(Equality.PROBABLY_EQUAL, Reason.BASIONYM_AUTHOR_CITED);
      }
      return recomb;
    }

    // at least one side has no combination author: the bracketed authors decide if both have one
    final var original = comparator.apply(b1, b2);
    if (original.equality() != Equality.UNKNOWN) {
      return original;
    }
    // an unbracketed author on one side, a bracketed one on the other: both are the original author
    AuthorshipComparison across = null;
    if (isEmpty(c1)) {
      across = comparator.apply(b1, c2);
    } else if (isEmpty(b1)) {
      across = comparator.apply(c1, b2);
    }
    if (across == null || across.equality() == Equality.UNKNOWN) {
      return original;
    }
    if (across.equality() == Equality.EQUAL) {
      return new AuthorshipComparison(Equality.EQUAL, Reason.BRACKETS);
    }
    return new AuthorshipComparison(Equality.PROBABLY_DIFFERENT, across.reason());
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
    Equality result = compareAuthorteam(a1, a2, minCommonSubstring, Integer.MAX_VALUE, 100, code);
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
   * Does an author comparison, normalizing the strings and try 3 comparisons:
   * 1) checks regular string equality
   * 2) checks for equality of the longest common substring
   * 3) do an author lookup and then check for common substring
   *
   * @param code the code determines which ex author to use. If null both authorteams are used for matching
   */
  @VisibleForTesting
  Equality compareAuthorteam(Authorship a1, Authorship a2, NomCode code) {
    return compareAuthorteam(a1, a2, minCommonSubstring, MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP, MIN_JARO_SURNAME_DISTANCE, code);
  }

  private Equality compareAuthorteam(@Nullable Authorship a1, @Nullable Authorship a2,
                                     final int minCommonSubstring, final int maxAuthorLengthWithoutLookup, final int jaroDistance,
                                     NomCode code
  ) {
    return authorteams(a1, a2, minCommonSubstring, maxAuthorLengthWithoutLookup, jaroDistance, code).equality();
  }

  private AuthorshipComparison authorteams(@Nullable Authorship a1, @Nullable Authorship a2,
                                           final int minCommonSubstring, final int maxAuthorLengthWithoutLookup,
                                           final int jaroDistance, NomCode code
  ) {
    var teams = compareTeams(a1, a2, minCommonSubstring, maxAuthorLengthWithoutLookup, jaroDistance, code);
    // X in Y: the name is X's, published in a work of Y, and cited either way, Natterer in Kuhl as Kuhl.
    // Two in citations are told apart by their own authors, Natterer in Kuhl is not Smith in Kuhl.
    // As Y may well have named the same epithet himself a few years on, the work only tells when the years agree.
    // See https://github.com/CatalogueOfLife/backend/issues/1652
    if (teams.equality() != Equality.EQUAL && a1 != null && a2 != null && a1.hasInAuthors() != a2.hasInAuthors()
        && new YearComparator(IN_YEAR_DIFF_ALLOWED, a1.getYear(), a2.getYear()).compare() != Equality.DIFFERENT) {
      var in = a1.hasInAuthors() ? new Authorship(a1.getInAuthors()) : new Authorship(a2.getInAuthors());
      var other = a1.hasInAuthors() ? a2 : a1;
      if (compareTeams(in, other, minCommonSubstring, maxAuthorLengthWithoutLookup, jaroDistance, code).equality() == Equality.EQUAL) {
        return new AuthorshipComparison(Equality.EQUAL, Reason.PUBLISHED_IN);
      }
    }
    return teams;
  }

  private AuthorshipComparison compareTeams(@Nullable Authorship a1, @Nullable Authorship a2,
                                            final int minCommonSubstring, final int maxAuthorLengthWithoutLookup,
                                            final int jaroDistance, NomCode code
  ) {
    // convert to all lower case, ascii only, no punctuation but commas seperating authors and normed whitespace
    List<String> authorTeam1 = normalizer.lookup(AuthorshipNormalizer.normalize(a1, code), maxAuthorLengthWithoutLookup, code);
    List<String> authorTeam2 = normalizer.lookup(AuthorshipNormalizer.normalize(a2, code), maxAuthorLengthWithoutLookup, code);
    if (!authorTeam1.isEmpty() && !authorTeam2.isEmpty()) {
      Equality equality = compareNormalizedAuthorteam(authorTeam1, authorTeam2, minCommonSubstring, jaroDistance);
      if (equality != Equality.EQUAL) {
        // try again by looking up entire author strings
        List<String> authorTeam1l = normalizer.lookup(authorTeam1, code);
        List<String> authorTeam2l = normalizer.lookup(authorTeam2, code);
        // only compare again if the queue is actually different then before
        if (!authorTeam1.equals(authorTeam1l) || !authorTeam2.equals(authorTeam2l)) {
          equality = compareNormalizedAuthorteam(authorTeam1l, authorTeam2l, minCommonSubstring, jaroDistance);
        }
      }
      return new AuthorshipComparison(equality, equality == Equality.EQUAL ? Reason.SAME_AUTHOR : Reason.DIFFERENT_AUTHOR);
    }
    // two anonymous works: only authors attributed on both sides could tell them apart, compared above
    if (a1 != null && a2 != null && a1.isAnonymous() && a2.isAnonymous()) {
      return new AuthorshipComparison(Equality.EQUAL, Reason.ANONYMOUS);
    }
    return AuthorshipComparison.MISSING;
  }

  /**
   * compares entire author team strings
   */
  private Equality compareNormalizedAuthorteam(final List<String> authorTeam1, final List<String> authorTeam2, final int minCommonStart, final int jaroDistance) {
    // quick check avoiding subsequent heavier processing
    if (authorTeam1.equals(authorTeam2)) {
      // we can stop here, authors are equal, thats enough
      return Equality.EQUAL;
      
    } else {
      // compare all authors to each other - a single match is good enough!
      // The authors of the second team are parsed once on first use and reused for every author of the first team
      final Author[] authors2 = new Author[authorTeam2.size()];
      for (String author1 : authorTeam1) {
        Author a1 = new Author(author1);
        int idx = 0;
        for (String author2 : authorTeam2) {
          Author a2 = authors2[idx] == null ? (authors2[idx] = new Author(author2)) : authors2[idx];
          idx++;
          if (Equality.EQUAL == compare(a1, a2, minCommonStart, jaroDistance)) {
            return Equality.EQUAL;
          }
        }
      }
    }
    return Equality.DIFFERENT;
  }

  private static double jaro(final String a1, final String a2) {
    var sim = JaroWinkler.similarity(a1, a2);
    // for really short names add some penalty
    if (a1.length() + a2.length() < 10) {
      sim = sim - (10 - a1.length() - a2.length()) * 5;
    }
    return sim;
  }

  /**
   * compares a single author potentially with initials
   */
  @VisibleForTesting
  static Equality compare(final Author a1, final Author a2, final int minCommonStart, final int jaroDistance) {
    // no shortcut on equal full names: authors built from their parts can share a full name and still differ in their suffix
    String common = StringUtils.getCommonPrefix(a1.surname, a2.surname);
    if (surnamesMatch(a1.surname, a2.surname, minCommonStart, jaroDistance)) {
      // do both names have a single initial which is different?
      // this is often the case when authors are relatives like brothers or son & father
      if (a1.initialsOrSuffixDiffer(a2)) {
        return Equality.DIFFERENT;
      } else {
        return Equality.EQUAL;
      }

    } else if (!a1.initialsOrSuffixDiffer(a2) && (a1.surname.equals(common) && (a2.surname.startsWith(common))
        || a2.surname.equals(common) && (a1.surname.startsWith(common)))
        ) {
      // short common surname, matching in full to one of them
      // and in addition existing and not conflicting initials
      return Equality.EQUAL;
      
    } else if (a1.fullname.equals(common) && (a2.surname.startsWith(common))
        || a2.fullname.equals(common) && (a1.surname.startsWith(common))
        ) {
      // the smallest common substring is the same as one of the inputs
      // if it also matches the start of the first longer surname then we are ok as the entire string is the best match we can have
      // likey a short abbreviation
      return Equality.EQUAL;

    } else if (!a1.initialsOrSuffixDiffer(a2) && compoundSurnamesMatch(a1, a2, minCommonStart, jaroDistance)) {
      // compound surnames like "Bory de Saint-Vincent" or "Kerner von Marilaun" are often cited by
      // their first part alone, which the last word based surname comparison above cannot see.
      // Still requires non conflicting initials so relatives stay apart.
      // https://github.com/CatalogueOfLife/backend/issues/1595
      return Equality.EQUAL;
    }
    return Equality.DIFFERENT;
  }

  /**
   * Compares the first part of one author's compound surname against the other author's plain surname.
   * The two first parts are deliberately never compared with each other: that part can just as well be a
   * middle name ("Conrad von Baldenstein" vs "Conrad von Buddenbrocks"), which would merge different authors.
   */
  private static boolean compoundSurnamesMatch(final Author a1, final Author a2, final int minCommonStart, final int jaroDistance) {
    return surnamesMatch(a1.surnamePrefix, a2.surname, minCommonStart, jaroDistance)
        || surnamesMatch(a1.surname, a2.surnamePrefix, minCommonStart, jaroDistance);
  }

  /**
   * The surname equality rule: identical, fuzzily similar or sharing a long enough common start.
   */
  private static boolean surnamesMatch(@Nullable final String s1, @Nullable final String s2, final int minCommonStart, final int jaroDistance) {
    if (s1 == null || s2 == null) {
      return false;
    }
    return s1.equals(s2) || jaro(s1, s2) > jaroDistance || StringUtils.getCommonPrefix(s1, s2).length() >= minCommonStart;
  }
  
}
