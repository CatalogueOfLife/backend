package life.catalogue.matching.authorship;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.AuthorshipNormalizer.Author;
import life.catalogue.matching.Equality;
import life.catalogue.matching.similarity.JaroWinkler;

import org.gbif.nameparser.api.NomCode;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;

/**
 * Compares authors as strings. Each is folded to lower case ASCII, read as initials and a surname and compared by a
 * rule cascade: identical, a Jaro-Winkler similarity, a common start, a compound surname cited by its first part.
 * Abbreviations are expanded with the author map of the code first. Two teams are equal as soon as any one author
 * of one equals any one author of the other.
 */
public class StringAuthorMatcher implements AuthorMatcher {
  private final AuthorshipNormalizer normalizer;

  public StringAuthorMatcher(AuthorshipNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  /**
   * Tries three comparisons: string equality, the surname rules, and the surname rules again once every author is
   * looked up in the author map.
   */
  @Override
  public Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
    final NomCode mapCode = ctx.code();
    List<String> authorTeam1 = normalizer.lookup(t1.authors(), mode.lookupShorterThan, mapCode);
    List<String> authorTeam2 = normalizer.lookup(t2.authors(), mode.lookupShorterThan, mapCode);
    Equality equality = compareNormalizedAuthorteam(authorTeam1, authorTeam2, mode.minCommonStart, mode.jaroDistance);
    if (equality != Equality.EQUAL) {
      // try again by looking up entire author strings
      List<String> authorTeam1l = normalizer.lookup(authorTeam1, mapCode);
      List<String> authorTeam2l = normalizer.lookup(authorTeam2, mapCode);
      // only compare again if the queue is actually different then before
      if (!authorTeam1.equals(authorTeam1l) || !authorTeam2.equals(authorTeam2l)) {
        equality = compareNormalizedAuthorteam(authorTeam1l, authorTeam2l, mode.minCommonStart, mode.jaroDistance);
      }
    }
    return equality;
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
      for (String author1 : authorTeam1) {
        Author a1 = new Author(author1);
        for (String author2 : authorTeam2) {
          Author a2 = new Author(author2);
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
    if (a1.equals(a2.fullname)) {
      // we can stop here, authors are equal, thats enough
      return Equality.EQUAL;
      
    } else {

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
