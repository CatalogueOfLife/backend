package life.catalogue.common.tax;

import java.util.regex.Pattern;

import static life.catalogue.common.text.StringUtils.foldToAscii;


/**
 * A scientific name normalizer that replaces common misspellings and epithet gender changes.
 */
public class SciNameNormalizer {
  
  private static final Pattern suffix_a = Pattern.compile("(?:on|um|us|a)$"); // is->e
  private static final Pattern suffix_i = Pattern.compile("ei$");
  private static final Pattern i = Pattern.compile("(?<!\\b)[jyi]+");
  private static final Pattern trh = Pattern.compile("([gtr])h", Pattern.CASE_INSENSITIVE);
  private static final Pattern removeRepeatedLetter = Pattern.compile("(\\p{L})\\1+");
  private static final Pattern removeHybridSignGenus = Pattern.compile("^\\s*[×xX]\\s*([A-Z])");
  private static final Pattern removeHybridSignEpithet = Pattern.compile("(?:^|\\s)(?:×\\s*|[xX]\\s+)([^A-Z])");
  private static final Pattern empty = Pattern.compile("[?!\"'`_-]");
  private static final Pattern punct = Pattern.compile("[,.:;]");
  private static final Pattern white = Pattern.compile("\\s{2,}");
  
  // dont use guava or commons so we dont have to bundle it for the solr cloud plugin ...
  public static boolean hasContent(String s) {
    return s != null && !(s.trim().isEmpty());
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
    
    // normalize whitespace
    s = empty.matcher(s).replaceAll("");
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
   * Normalizes and entire scientific name, keeping monomials or the first genus part rather unchanged,
   * applying the more drastic normalization to the remainder of the name only.
   */
  public static String normalize(String s, boolean stemming) {
    return normalize(s, false, stemming);
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
    s = removeHybridSignGenus.matcher(s).replaceAll("$1");
    s = removeHybridSignEpithet.matcher(s).replaceAll(" $1");
    
    // Only for bi/trinomials, otherwise we mix up ranks.
    if (normMonomials) {
      s = normStrongly(s, stemming);
      
    } else if (s.indexOf(' ') > 2) {
      String[] parts = s.split(" ", 2);
      s = parts[0] + " " + normStrongly(parts[1], stemming);
    }
    
    return s.trim();
  }
  
  private static String normStrongly(String s, boolean stemming) {
    // remove repeated letters→leters in binomials
    s = removeRepeatedLetter.matcher(s).replaceAll("$1");
    
    if (stemming) {
      s = stemEpithet(s);
    }
    // normalize frequent variations of i
    s = i.matcher(s).replaceAll("i");
    if (stemming) {
      s = suffix_i.matcher(s).replaceAll("i");
    }
    // normalize frequent variations of t/r sometimes followed by an 'h'
    return trh.matcher(s).replaceAll("$1");
  }
  
  /**
   * Does a stemming of a latin epithet and return the female version ending with 'a'.
   */
  public static String stemEpithet(String epithet) {
    if (!hasContent(epithet)) return "";
    return suffix_a.matcher(epithet).replaceFirst("a");
  }
  
}
