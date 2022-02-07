package life.catalogue.common.text;

import it.unimi.dsi.fastutil.ints.Int2CharMap;

import it.unimi.dsi.fastutil.ints.Int2CharMaps;

import it.unimi.dsi.fastutil.ints.Int2CharOpenHashMap;

import life.catalogue.common.io.LineReader;
import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.NameFormatter;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import it.unimi.dsi.fastutil.chars.CharSet;

import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities dealing with unicode strings
 */
public class UnicodeUtils {
  private static final Logger LOG = LoggerFactory.getLogger(UnicodeUtils.class);
  private static final boolean DEBUG = false;
  // loads homoglyphs from resources taken from https://raw.githubusercontent.com/codebox/homoglyph/master/raw_data/chars.txt
  private static final Int2CharMap HOMOGLYHPS; // unicode codepoints as keys to avoid dealing with chars & surrogate pairs
  private static final int HOMOGLYHPS_LOWEST_CP;
  private static final int HOMOGLYHPS_HIGHEST_CP;
  static {
    var lr = new LineReader(Resources.stream("unicode/homoglyphs.txt"));
    Int2CharMap homoglyphs = new Int2CharOpenHashMap();
    final AtomicInteger minCP = new AtomicInteger(Integer.MAX_VALUE);
    final AtomicInteger maxCP = new AtomicInteger(Integer.MIN_VALUE);
    StringBuilder canonicals = new StringBuilder();
    for (String line : lr) {
      // the canonical is never a surrogate pair
      char canonical = line.charAt(0);
      // ignore whitespace
      if (' ' == canonical) {
        continue;
      }
      if (DEBUG) {
        System.out.print(canonical + " ");
        System.out.println((int)canonical);
      }
      canonicals.append(canonical);

      // ignore all ASCII chars from homoglyphs
      final AtomicInteger counter = new AtomicInteger();
      line.substring(1).codePoints()
          // remove hybrid marker which we use often
          .filter(cp -> cp > 128 && cp != NameFormatter.HYBRID_MARKER)
          .forEach(
            cp -> {
              if (DEBUG) {
                System.out.print("  ");
                System.out.print(Character.toChars(cp));
                System.out.print(" ");
                System.out.print(cp);
                System.out.println("  " + Character.getName(cp));
              }
              homoglyphs.put(cp, canonical);
              minCP.set( Math.min(minCP.get(), cp) );
              maxCP.set( Math.max(maxCP.get(), cp) );
              counter.incrementAndGet();
            }
          );
      canonicals.append("[" + counter + "] ");
      if (lr.getRow() > 175 || 'ɸ' == canonical) {
        // skip all rare chars
        break;
      }
    }
    HOMOGLYHPS = Int2CharMaps.unmodifiable(homoglyphs);
    HOMOGLYHPS_LOWEST_CP = minCP.get();
    HOMOGLYHPS_HIGHEST_CP = maxCP.get();
    LOG.info("Loaded known homoglyphs: {}", canonicals);
    LOG.debug("Min homoglyph codepoint: {}", minCP);
    LOG.debug("Max homoglyph codepoint: {}", maxCP);
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
    return findHomoglyph(cs) >= 0;
  }


  /**
   * Returns the unicode codepoint of the first character which is a known homoglyph of a latin character
   * or -1 if none could be found.
   */
  public static int findHomoglyph(final CharSequence cs) {
    if (cs == null) {
      return -1;
    }
    var iter = cs.codePoints().iterator();
    while(iter.hasNext()) {
      final int cp = iter.nextInt();
      if (HOMOGLYHPS_LOWEST_CP <= cp && cp <= HOMOGLYHPS_HIGHEST_CP && HOMOGLYHPS.containsKey(cp)) {
        return cp;
      }
    }
    return -1;
  }

  /**
   * Replaces all known homoglyphs with their canonical character.
   */
  public static String replaceHomoglyphs(final CharSequence cs) {
    if (cs == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    var iter = cs.codePoints().iterator();
    while(iter.hasNext()) {
      final int cp = iter.nextInt();
      if (HOMOGLYHPS_LOWEST_CP <= cp && cp <= HOMOGLYHPS_HIGHEST_CP && HOMOGLYHPS.containsKey(cp)) {
        sb.append(HOMOGLYHPS.get(cp));
      } else {
        sb.appendCodePoint(cp);
      }
    }
    return sb.toString();
  }
}
