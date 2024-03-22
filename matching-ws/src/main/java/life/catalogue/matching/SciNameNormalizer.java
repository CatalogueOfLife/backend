package life.catalogue.matching;


import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.gbif.nameparser.util.UnicodeUtils.foldToAscii;


/**
 * FIXME: Copied from `api` module to avoid pulling in transitive dependencies.
 *
 * A scientific name normalizer that replaces common misspellings and epithet gender changes by stemming.
 *
 * Articles on nomenclature
 * https://code.iczn.org/formation-and-treatment-of-names/article-31-species-group-names/
 * http://dx.doi.org/10.11646/zootaxa.3710.5.1
 *
 * Latin resources
 * https://www.nationalarchives.gov.uk/latin/stage-1-latin/resources/stage-1-latin-grammar-resource/adjectives/
 * http://people.hsc.edu/drjclassics/Latin/general_info_about_grammar/agreement.shtm
 */
public class SciNameNormalizer {
  // honorifics: -i/-ae/-orum, see https://code.iczn.org/formation-and-treatment-of-names/article-26-assumption-of-greek-or-latin-in-scientific-names/?frame=1
  // singular: us/er/i/o/um, a/ae/am
  // plural: i/orum/is/os, ae/arum/is/as
  private static final Pattern suffix_a = Pattern.compile("(?:arum|orum|us|um|ae|am|is|os|as|[iey]?i|o|a|e)$");
  // most adjectives lose the ‘e’ when declined, some don't though, eg. liber
  // pulcher, pulchra, pulchrum BUT liber, libera, liberum
  private static final Pattern suffix_ra = Pattern.compile("(?<=[a-z][^aeiou])r(?:a|um)$");
  // tor/trix, e.g. viator / viatrix
  private static final Pattern suffix_tor = Pattern.compile("trix$");
  private static final Pattern i = Pattern.compile("(?<!\\b)[jyi]+");
  private static final Pattern trh = Pattern.compile("([gtr])h", Pattern.CASE_INSENSITIVE);
  private static final Pattern removeRepeatedLetter = Pattern.compile("(\\p{L})\\1+");
  private static final Pattern removeHybridSignGenus = Pattern.compile("^\\s*[×xX]\\s*([A-Z])");
  private static final Pattern removeHybridSignEpithet = Pattern.compile("(?:^|\\s)(?:×\\s*|[xX]\\s+)([^A-Z])");
  private static final Pattern empty = Pattern.compile("[?!\"'`_-]");
  private static final Pattern punct = Pattern.compile("[,.:;]");
  private static final Pattern white = Pattern.compile("\\s{2,}");
  // ††️‡
  public final static Pattern dagger = Pattern.compile("[\\u2020\\u2021\\u271D]");

  // dont use guava or commons so we dont have to bundle it for the solr cloud plugin ...
  public static boolean hasContent(String s) {
    return s != null && !(s.trim().isEmpty());
  }

  /**
   * Removes the dagger symbol from a name string
   */
  public static String removeDagger(String name) {
    if (name == null) return null;
    return trimToNull(dagger.matcher(name).replaceAll(""));
  }

  /**
   * Removes a hybrid cross, or a likely hybrid cross.
   */
  public static String removeHybridMarker(String name) {
    if (name == null) return null;
    name = removeHybridSignGenus.matcher(name).replaceAll("$1");
    name = removeHybridSignEpithet.matcher(name).replaceAll(" $1");

    return name;
  }

  /**
   * Folds a name into its ASCII equivalent,
   * replaces all punctuation with space
   * removes hyphens and apostrophes
   * and finally trims and normalizes whitespace to a single ASCII space.
   */
  public static String normalizedAscii(String s) {
    if (s == null) return null;

    // Normalize letters and ligatures to their ASCII equivalent
    s = foldToAscii(s);

    // normalize hyphens, apostrophes, punctuation and whitespace
    s = empty.matcher(s).replaceAll("");
    return normalizeWhitespaceAndPunctuation(s);
  }

  /**
   * Replaces all punctuation with space and trims and normalizes whitespace to a single ASCII space.
   */
  public static String normalizeWhitespaceAndPunctuation(String s) {
    if (s == null) return null;
    // normalize whitespace
    s = punct.matcher(s).replaceAll(" ");
    s = white.matcher(s).replaceAll(" ");
    return s.trim();
  }



  /**
   * Normalizes the entire scientific name, keeping monomials or the first genus part rather unchanged,
   * applying the more drastic normalization incl stemming to the remainder of the name only.
   * The return will be a strictly ASCII encoded string.
   */
  public static String normalize(String s) {
    return normalize(s, false, true);
  }

  /**
   * Normalizes an entire name string including monomials and genus parts of a name.
   */
  public static String normalizeAll(String s) {
    return normalize(s, true, true);
  }

  private static String normalize(String s, boolean normMonomials, boolean stemming) {
    if (!hasContent(s)) return "";

    s = normalizedAscii(s);

    // Remove a hybrid cross, or a likely hybrid cross.
    s = removeHybridMarker(s);

    // Only for bi/trinomials, otherwise we mix up ranks.
    if (normMonomials) {
      s = normStrongly(s, stemming);

    } else if (s.indexOf(' ') > 2) {
      String[] parts = s.split(" +");
      StringBuilder sb = new StringBuilder();
      sb.append(parts[0]);
      for (int i = 1; i < parts.length; i++) {
        sb.append(" ");
        if (Character.isLowerCase(parts[i].charAt(0))) {
          sb.append(normStrongly(parts[i], stemming));
        } else {
          sb.append(parts[i]);
        }
      }
      s = sb.toString();
    }

    return s.trim();
  }

  private static String normStrongly(String s, boolean stemming) {
    if (stemming) {
      s = stemEpithet(s);
    }
    // normalize frequent variations of i
    s = i.matcher(s).replaceAll("i");
    // remove repeated letters→leters in binomials
    s = removeRepeatedLetter.matcher(s).replaceAll("$1");
    // normalize frequent variations of t/r sometimes followed by an 'h'
    return trh.matcher(s).replaceAll("$1");
  }

  /**
   * Stems and normalizes some few, but frequent misspellings
   */
  public static String normalizeEpithet(String epithet) {
    return normStrongly(epithet, true);
  }

  /**
   * Does a stemming of a latin epithet removing any gender carrying suffix.
   * See https://www.iapt-taxon.org/nomen/pages/main/art_62.html
   *
   * For generic latin stemming see the Schinke Latin stemming algorithm is described in:
   * Schinke R, Greengrass M, Robertson AM and Willett P (1996) A stemming algorithm for Latin text databases. Journal of Documentation, 52: 172-187.
   *
   * http://snowballstem.org/otherapps/schinke/
   * http://caio.ueberalles.net/a_stemming_algorithm_for_latin_text_databases-schinke_et_al.pdf
   *
   * See also https://github.com/gnames/gnparser/blob/master/ent/stemmer/stemmer.go
   */
  public static String stemEpithet(String epithet) {
    if (!hasContent(epithet)) return "";
    epithet = suffix_tor.matcher(epithet).replaceFirst("tor");
    epithet = suffix_ra.matcher(epithet).replaceFirst("er"); // there are some cases (e.g. liber) that do not lose the "e" - but we rather catch the majority of cases
    return suffix_a.matcher(epithet).replaceFirst("");
  }

}