package life.catalogue.common.text;

import org.gbif.nameparser.util.UnicodeUtils;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.text.WordUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;

import static life.catalogue.common.tax.NameFormatter.HYBRID_MARKER;

/**
 * Utils class adding specific string methods to existing guava {@link Strings} and commons {@link org.apache.commons.lang3.StringUtils}.
 */
public class StringUtils {

  public static String[] EMPTY_STRING_ARRAY = new String[0];
  private static final char[] hexCode = "0123456789ABCDEF".toCharArray();
  private static final CharMatcher NON_DIGITLETTER = CharMatcher.javaLetterOrDigit().negate();
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern SPACE = Pattern.compile("[\\u00A0\\u2000-\\u200A\\u2028\\u2029\\u202F]"); // https://en.wikipedia.org/wiki/General_Punctuation
  private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202C\\u202F\\u2060-\\u206F]");
  public static final Pattern EMAIL_EXTRACTION = Pattern.compile("\\b[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+){1,10}\\b");

  private StringUtils() {}

  /**
   * @return true if at least one of the strings is non empty
   */
  public static boolean hasContent(String... strings) {
    for (String s : strings) {
      if (!org.apache.commons.lang3.StringUtils.isEmpty(s)) {
        return true;
      }
    }
    return false;
  }

  public static boolean equalsIgnoreCase(String s1, String s2) {
    if (s1 == null || s2 == null) {
      return Objects.equals(s1, s2);
    }
    return s1.equalsIgnoreCase(s2);
  }

  public static boolean equalsIgnoreCaseAndSpace(String s1, String s2) {
    if (s1 == null || s2 == null) {
      return Objects.equals(s1, s2);
    }
    String n1 = WHITESPACE.matcher(s1).replaceAll("");
    String n2 = WHITESPACE.matcher(s2).replaceAll("");
    return n1.equalsIgnoreCase(n2);
  }

  public static boolean equalsDigitOrAsciiLettersIgnoreCase(String s1, String s2) {
    s1 = digitOrAsciiLetters(s1);
    s2 = digitOrAsciiLetters(s2);
    return equalsIgnoreCaseAndSpace(s1, s2);
  }

  /**
   * Concatenates the given parts with a space, skipping any null or empty strings
   */
  public static String concatWS(String... parts) {
    return concat(" ", parts);
  }

  /**
   * Concatenates the given parts with a given delimiter, skipping any null or empty strings
   */
  public static String concat(String delimiter, String... parts) {
    if (parts == null)
      return null;
    StringBuilder sb = new StringBuilder();
    append(sb, delimiter, false, parts);
    return sb.toString();
  }

  /**
   * Concatenates the given parts with a given delimiter, skipping any null or empty strings
   */
  public static String concat(String delimiter, Collection<? extends Object> values) {
    if (values == null) return null;
    return concat(delimiter, values.stream().map(Object::toString));
  }

  /**
   * Concatenates the given parts with a given delimiter, skipping any null or empty strings
   */
  public static String concat(String delimiter, Stream<String> values) {
    if (values == null) return null;
    return org.apache.commons.lang3.StringUtils.trimToNull(values.collect(Collectors.joining(delimiter)));
  }

  public static void append(StringBuilder sb, String delimiter, boolean delimiterBeforeFirstPart, String... parts) {
    boolean first = true;
    if (parts != null) {
      for (String p : parts) {
        if (!org.apache.commons.lang3.StringUtils.isBlank(p)) {
          if (sb.length() > 0 && (!first || delimiterBeforeFirstPart)) {
            sb.append(delimiter);
          }
          first = false;
          sb.append(p.trim());
        }
      }
    }
  }

  public static String hexString(byte[] data) {
    StringBuilder r = new StringBuilder(data.length * 2);
    int counter = 0;
    for (byte b : data) {
      r.append(hexCode[(b >> 4) & 0xF]);
      r.append(hexCode[(b & 0xF)]);
      counter++;
      if (counter % 16 == 0) {
        r.append("\n");
      } else if (counter % 2 == 0) {
        r.append(" ");
      }
    }
    return r.toString();
  }

  /**
   * Increase a given string by 1, i.e. increase the last char in that string by one. If its a z or Z the char before is increased instead
   * and a new char a is appended. Only true letters are increased, but spaces, punctuation or numbers remain unchanged. Null values stay
   * null and empty strings empty. The case of existing characters will be kept and the appended chars will use the case of the last char of
   * the original string.
   * <p>
   * For example "Carla" becomes "Carlb", "Atz" -> "Aua", "zZz" -> "aAaa" or "Abies zzz" -> "Abiet aaa".
   *
   * @param x
   * @return
   */
  public static String increase(String x) {
    if (x == null) {
      return null;
    }
    if (x.equals("")) {
      return x;
    }

    char[] chars = x.toCharArray();
    int idx = chars.length - 1;
    boolean appendingNeeded = false;
    Character lastOriginalChar = null;

    while (idx >= 0) {
      char c = chars[idx];
      if (!Character.isLetter(c)) {
        idx--;
        continue;
      }

      if (lastOriginalChar == null) {
        lastOriginalChar = c;
      }

      if (c == 'z') {
        chars[idx] = 'a';
        appendingNeeded = true;

      } else if (c == 'Z') {
        chars[idx] = 'A';
        appendingNeeded = true;

      } else {
        c++;
        chars[idx] = c;
        appendingNeeded = false;
        break;
      }
      idx--;
    }

    // first char, also append to end
    if (appendingNeeded) {
      char append =
          (lastOriginalChar == null || Character.isLowerCase(lastOriginalChar)) ? 'a' : 'A';
      return String.valueOf(chars) + append;

    }
    return String.valueOf(chars);
  }

  /**
   * Splits a string at the last occurrence of the given delimiter. If delimiter exists an array with 2 strings is returned. If not, null is
   * returned.
   * 
   * @return null or an array with the 2 split strings excluding the delimiter
   */
  public static String[] splitRight(String s, char delimiter) {
    int i = s.lastIndexOf(delimiter);
    if (i > 0) {
      return new String[] {s.substring(0, i), s.substring(i + 1)};
    }
    return null;
  }

  public boolean allEmpty(String... strings) {
    for (String s : strings) {
      if (!org.apache.commons.lang3.StringUtils.isEmpty(s)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns an uppercase ASCII string for the given input. Replaces all non digit or letter characters with a single space, including
   * invisible control characters, underscore, dashes and punctuation and converts that to upper case and trims and normalizes whitespace;
   *
   * @param x any input string used for parsing a single valuejsonEscape
   * @return the cleaned, normalized string
   */
  public static String digitOrAsciiLetters(String x) {
    if (x == null)
      return null;
    x = NON_DIGITLETTER.trimAndCollapseFrom(x, ' ');
    x = UnicodeUtils.foldToAscii(x);
    x = x.toUpperCase();
    return Strings.emptyToNull(x);
  }

  /**
   * <p>
   * Converts a string to a Set. Breaks the string to characters and store each character in a Set.
   *
   * @param string the string to convert
   * @return a <code>Set</code> containing all characters in the text string parameter
   */
  public static Set<Character> charSet(String string) {
    Set<Character> chars = new HashSet<>();
    for (int i = 0; i < string.length(); i++) {
      chars.add(string.charAt(i));
    }
    return chars;
  }

  /**
   * Zero-pads the provided string to the provided width.
   * 
   * @param str
   * @param width
   * @return
   */
  public static String zpad(String str, int width) {
    return lpad(str, width, '0');
  }

  /**
   * Left-pads the provided string to the specified width using space as the padding character
   * 
   * @param obj
   * @param width
   * @return
   */
  public static String lpad(String obj, int width) {
    return lpad(obj, width, ' ');
  }

  /**
   * Left-pads the provided string to the specified width using the specified padding character. Null-safe.
   * 
   * @param str
   * @param width
   * @param padChar
   * @return
   */
  public static String lpad(String str, int width, char padChar) {
    if (str == null) {
      str = "";
    } else if (str.length() >= width) {
      return str;
    }
    StringBuilder sb = new StringBuilder(width);
    for (int i = str.length(); i < width; ++i) {
      sb.append(padChar);
    }
    sb.append(str);
    return sb.toString();
  }

  public static String removeHybrid(String x) {
    if(x != null && !x.isEmpty() && x.charAt(0) == HYBRID_MARKER) {
      return x.substring(1);
    }
    return x;
  }

  public static String camelCase(Enum<?> val) {
    if (val == null) return null;
    return WordUtils.capitalizeFully(val.name(), new char[]{'_'}).replaceAll("_", "");
  }

  public static String lowerCamelCase(Enum<?> val) {
    if (val == null) return null;
    char c[] = camelCase(val).toCharArray();
    c[0] = Character.toLowerCase(c[0]);
    return new String(c);
  }

  /**
   * Human readable size using binary units based on 1024
   */
  public static String byteWithUnitBinary(long bytes) {
    long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
    if (absB < 1024) {
      return bytes + " B";
    }
    long value = absB;
    CharacterIterator ci = new StringCharacterIterator("KMGTPE");
    for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
      value >>= 10;
      ci.next();
    }
    value *= Long.signum(bytes);
    return String.format("%.1f %ciB", value / 1024.0, ci.current());
  }

  /**
   * Human readable size using SI units based on 1000
   */
  public static String byteWithUnitSI(long bytes) {
    if (-1000 < bytes && bytes < 1000) {
      return bytes + " B";
    }
    CharacterIterator ci = new StringCharacterIterator("kMGTPE");
    while (bytes <= -999_950 || bytes >= 999_950) {
      bytes /= 1000;
      ci.next();
    }
    return String.format("%.1f %cB", bytes / 1000.0, ci.current());
  }

  public static String cleanInvisible(String value) {
    if (value == null)
      return value;
    return INVISIBLE.matcher(
      SPACE.matcher(value).replaceAll(" ")
    ).replaceAll("");
  }

  public static String extractEmail(String value) {
    if (value != null) {
      var m = EMAIL_EXTRACTION.matcher(value.replaceAll("\\s+", ""));
      if (m.find()) {
        return m.group();
      }
    }
    return null;
  }

}
