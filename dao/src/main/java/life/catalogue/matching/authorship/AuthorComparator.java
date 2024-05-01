package life.catalogue.matching.authorship;

import com.google.common.annotations.VisibleForTesting;

import life.catalogue.api.model.ScientificName;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;

import life.catalogue.matching.similarity.JaroWinkler;

import org.gbif.nameparser.api.Authorship;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private final int minCommonSubstring;
  
  public AuthorComparator(AuthorshipNormalizer normalizer) {
    this.normalizer = normalizer;
    minCommonSubstring = 4;
  }
  
  /**
   * Compares the authorteams and year of two names.
   * If given both the year and authorteam needs to match to yield an EQUAL,
   * with a small difference of 2 years being accepted.
   */
  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2) {
    // compare year first - simpler to calculate
    Equality result = new YearComparator(1, a1, a2).compare();
    // compare authors if it's not already different
    if (result != Equality.DIFFERENT) {
      var aresult = compareAuthorteam(a1, a2, minCommonSubstring, MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP);
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
    // compare year first - simpler to calculate
    Equality result = compareAuthorteam(a1, a2, minCommonSubstring, MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP);
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
    Equality recomb = compare(n1.getCombinationAuthorship(), n2.getCombinationAuthorship());
    if (recomb != Equality.UNKNOWN) {
      // in case the recomb author differs or is the same we are done, no need for basionym authorship comparison
      return recomb;
    }
    Equality original = compare(n1.getBasionymAuthorship(), n2.getBasionymAuthorship());
    if (original == Equality.UNKNOWN) {
      // a common error is missing brackets, so if all is unknown we compare authorship across brackets and return a possible match
      Equality across = Equality.UNKNOWN;
      if (n1.getCombinationAuthorship().isEmpty()) {
        across = compare(n1.getBasionymAuthorship(), n2.getCombinationAuthorship());
      } else if (n1.getBasionymAuthorship().isEmpty()) {
        across = compare(n1.getCombinationAuthorship(), n2.getBasionymAuthorship());
      }
      return across == Equality.EQUAL ? Equality.EQUAL : Equality.UNKNOWN;
    }
    return recomb.and(original);
  }
  
  /**
   * Compares two sets of author & year for equality.
   * This is more strict than the normal compare method and requires both authors and year to match.
   * Author matching is still done fuzzily
   *
   * @return true if both sets match
   */
  public boolean compareStrict(Authorship a1, Authorship a2) {
    // strictly compare authors first
    Equality result = compareAuthorteam(a1, a2, minCommonSubstring, Integer.MAX_VALUE);
    if (result != Equality.EQUAL) {
      return false;
    }
    // now also compare the year
    if (a1.getYear() == null && a2.getYear() == null) {
      return true;
    }
    return Equality.EQUAL == new YearComparator(a1.getYear(), a2.getYear()).compare();
  }
  
  /**
   * Does an author comparison, normalizing the strings and try 3 comparisons:
   * 1) checks regular string equality
   * 2) checks for equality of the longest common substring
   * 3) do an author lookup and then check for common substring
   */
  private Equality compareAuthorteam(@Nullable Authorship a1, @Nullable Authorship a2, int minCommonSubstring, int maxAuthorLengthWithoutLookup) {
    // convert to all lower case, ascii only, no punctuation but commas seperating authors and normed whitespace
    List<String> authorTeam1 = normalizer.lookup(AuthorshipNormalizer.normalize(a1), maxAuthorLengthWithoutLookup);
    List<String> authorTeam2 = normalizer.lookup(AuthorshipNormalizer.normalize(a2), maxAuthorLengthWithoutLookup);
    if (!authorTeam1.isEmpty() && !authorTeam2.isEmpty()) {
      Equality equality = compareNormalizedAuthorteam(authorTeam1, authorTeam2, minCommonSubstring);
      if (equality != Equality.EQUAL) {
        // try again by looking up entire author strings
        List<String> authorTeam1l = normalizer.lookup(authorTeam1);
        List<String> authorTeam2l = normalizer.lookup(authorTeam2);
        // only compare again if the queue is actually different then before
        if (!authorTeam1.equals(authorTeam1l) || !authorTeam2.equals(authorTeam2l)) {
          equality = compareNormalizedAuthorteam(authorTeam1l, authorTeam2l, minCommonSubstring);
        }
      }
      return equality;
    }
    return Equality.UNKNOWN;
  }
  
  private static int lengthWithoutWhitespace(String x) {
    return StringUtils.deleteWhitespace(x).length();
  }
  
  /**
   * compares entire author team strings
   */
  private Equality compareNormalizedAuthorteam(final List<String> authorTeam1, final List<String> authorTeam2, final int minCommonStart) {
    // quick check avoiding subsequent heavier processing
    if (authorTeam1.equals(authorTeam2)) {
      // we can stop here, authors are equal, thats enough
      return Equality.EQUAL;
      
    } else {
      // compare all authors to each other - a single match is good enough!
      for (String author1 : authorTeam1) {
        Author a1 = new Author(author1);
        for (String author2 : authorTeam2) {
          Author a2 = new Author(author2);
          if (Equality.EQUAL == compare(a1, a2, minCommonStart)) {
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
  static Equality compare(final Author a1, final Author a2, final int minCommonStart) {
    if (a1.equals(a2.fullname)) {
      // we can stop here, authors are equal, thats enough
      return Equality.EQUAL;
      
    } else {

      String common = StringUtils.getCommonPrefix(a1.surname, a2.surname);
      if (a1.surname.equals(a2.surname) || jaro(a1.surname, a2.surname) > 90 || common.length() >= minCommonStart) {
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
        
      } else if (lengthWithoutWhitespace(StringUtils.getCommonPrefix(a1.fullname, a2.fullname)) > minCommonStart) {
        // the author string incl initials but without whitespace shares at least minCommonStart+1 characters
        return Equality.EQUAL;
        
      }
    }
    return Equality.DIFFERENT;
  }
  
}
