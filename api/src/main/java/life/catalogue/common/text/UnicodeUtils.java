package life.catalogue.common.text;

import life.catalogue.common.io.LineReader;
import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.NameFormatter;

import java.text.Normalizer;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import it.unimi.dsi.fastutil.chars.CharSet;

/**
 * Utilities dealing with unicode strings
 */
public class UnicodeUtils {
  // loads homoglyphs from resources taken from https://raw.githubusercontent.com/codebox/homoglyph/master/raw_data/chars.txt
  private final static char[] HOMOGLYHPS;
  private final static int HOMOGLYHPS_LENGTH;
  private final static int HOMOGLYHPS_LOWEST_CP;
  static {
    //TODO: load file into huge Pattern ???
    var lr = new LineReader(Resources.stream("unicode/homoglyphs.txt"));
    CharSet homoglyphs = new CharOpenHashSet();
    for (String line : lr) {
      char canonical = line.charAt(0);
      // ignore whitespace
      if (' ' == canonical) {
        continue;
      }

      // ignore all ASCII chars from homoglyphs
      line.substring(1).chars()
          .filter(c -> c > 128)
          .forEach(
            c -> homoglyphs.add((char)c)
          );
      if (lr.getRow() > 175 || 'ɸ' == canonical) {
        // skip all rare chars
        break;
      }
    }
    // remove hybrid marker which we use often
    homoglyphs.remove(NameFormatter.HYBRID_MARKER);

    HOMOGLYHPS = homoglyphs.toCharArray();
    HOMOGLYHPS_LENGTH = HOMOGLYHPS.length;
    Arrays.sort(HOMOGLYHPS);
    HOMOGLYHPS_LOWEST_CP = HOMOGLYHPS[0];
  }

  /**
   * Replaces all diacretics with their ascii counterpart.
   */
  public static String ascii(String x) {
    if (x == null) {
      return null;
    }
    // manually normalize characters not dealt with by the java Normalizer
    x = StringUtils.replaceChars(x, "øØðÐ", "oOdD");

    // use java unicode normalizer to remove accents and punctuation
    x = Normalizer.normalize(x, Normalizer.Form.NFD);
    x = x.replaceAll("\\p{M}", "");
    return x;
  }

  /**
   * Replaces all digraphs and ligatures with their underlying 2 latin letters.
   *
   * @param x the string to decompose
   */
  public static String decompose(String x) {
    if (x == null) {
      return null;
    }
    return x.replaceAll("æ", "ae")
        .replaceAll("Æ", "Ae")
        .replaceAll("œ", "oe")
        .replaceAll("Œ", "Oe")
        .replaceAll("Ĳ", "Ij")
        .replaceAll("ĳ", "ij")
        .replaceAll("ǈ", "Lj")
        .replaceAll("ǉ", "lj")
        .replaceAll("ȸ", "db")
        .replaceAll("ȹ", "qp")
        .replaceAll("ß", "ss")
        .replaceAll("ﬆ", "st")
        .replaceAll("ﬅ", "ft")
        .replaceAll("ﬀ", "ff")
        .replaceAll("ﬁ", "fi")
        .replaceAll("ﬂ", "fl")
        .replaceAll("ﬃ", "ffi")
        .replaceAll("ﬄ", "ffl");
  }

  /**
   * Returns true if there is at least on character which is a known homoglyph of a latin character.
   */
  public static boolean containsHomoglyphs(final CharSequence cs) {
    if (cs == null) {
      return false;
    }
    // we use a modified StringUtils.containsAny method, skipping lower code point comparisons
    // this is magnitudes faster than a regex or the regular StringUtils.containsAny method when using many homoglyph chars
    // tested on a modern MacBook Pro with 1688 chars to run 10 million tests in 1.4s no matter if homoglyphs existed in the test string
    final int csLength = cs.length();
    final int csLast = csLength - 1;
    final int searchLast = HOMOGLYHPS_LENGTH - 1;
    for (int i = 0; i < csLength; i++) {
      final char ch = cs.charAt(i);
      // homoglyphs are never in the lower ascii code range!
      if (ch >= HOMOGLYHPS_LOWEST_CP) {
        for (int j = 0; j < HOMOGLYHPS_LENGTH; j++) {
          if (HOMOGLYHPS[j] == ch) {
            if (Character.isHighSurrogate(ch)) {
              if (j == searchLast) {
                // missing low surrogate, fine, like String.indexOf(String)
                return true;
              }
              if (i < csLast && HOMOGLYHPS[j + 1] == cs.charAt(i + 1)) {
                return true;
              }
            } else {
              // ch is in the Basic Multilingual Plane
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
