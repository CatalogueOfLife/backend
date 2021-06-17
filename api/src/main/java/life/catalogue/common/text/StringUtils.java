package life.catalogue.common.text;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import org.apache.commons.text.WordUtils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.text.CharacterIterator;
import java.text.Normalizer;
import java.text.StringCharacterIterator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static life.catalogue.common.tax.NameFormatter.HYBRID_MARKER;

/**
 * Utils class adding specific string methods to existing guava {@link Strings} and commons {@link org.apache.commons.lang3.StringUtils}.
 */
public class StringUtils {

  public static String[] EMPTY_STRING_ARRAY = new String[0];
  
  private static Pattern MARKER = Pattern.compile("\\p{M}");
  private static final Pattern OCT = Pattern.compile("^[0-7]+$");
  private static final Pattern HEX = Pattern.compile("^[0-9abcdefABCDEF]+$");
  private static final char[] hexCode = "0123456789ABCDEF".toCharArray();
  private static final CharMatcher NON_DIGITLETTER = CharMatcher.javaLetterOrDigit().negate();
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

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
    for (String p : parts) {
      if (!org.apache.commons.lang3.StringUtils.isBlank(p)) {
        if (sb.length() > 0) {
          sb.append(delimiter);
        }
        sb.append(p.trim());
      }
    }
    return sb.toString();
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
   * Removes accents & diacretics and converts ligatures into several chars
   *
   * There are still a few unicode characters which are not captured by the java Normalizer and this method,
   * so if you rely on true ASCII to be generated make sure to call the removeNonAscii(x) method afterwards!
   *
   * @param x string to fold into ASCII
   * @return string converted to ASCII equivalent, expanding common ligatures
   */
  public static String foldToAscii(String x) {
    if (x == null) {
      return null;
    }
    x = replaceSpecialCases(x);
    // use java unicode normalizer to remove accents
    x = Normalizer.normalize(x, Normalizer.Form.NFD);
    return MARKER.matcher(x).replaceAll("");
  }

  /**
   * Removes all characters that are not ASCII chars, i.e. above the first 7 bits
   */
  public static String removeNonAscii(String x) {
    if (x == null) return null;
    char[] out = new char[x.length()];
    int j = 0;
    for (int i = 0, n = x.length(); i < n; ++i) {
      char c = x.charAt(i);
      if (c <= '\u007F') out[j++] = c;
    }
    return new String(out);
  }

  /**
   * Replaces all characters that are not ASCII chars, i.e. above the first 7 bits, with the given replacement char
   */
  public static String replaceNonAscii(String x, char replacement) {
    if (x == null) return null;
    char[] out = new char[x.length()];
    int j = 0;
    for (int i = 0, n = x.length(); i < n; ++i) {
      char c = x.charAt(i);
      out[j++] = c <= '\u007F' ? c : replacement;
    }
    return new String(out);
  }

  /**
   * The Normalizer misses a few cases and 2 char ligatures which we deal with here
   */
  private static String replaceSpecialCases(String x) {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < x.length(); i++) {
      char c = x.charAt(i);
      switch (c) {
        case 'ß':
          sb.append("ss");
          break;
        case 'ſ':
          sb.append("s");
          break;
        case 'Æ':
          sb.append("AE");
          break;
        case 'æ':
          sb.append("ae");
          break;
        case 'Ð':
          sb.append("D");
          break;
        case 'đ':
          sb.append("d");
          break;
        case 'ð':
          sb.append("d");
          break;
        case 'Ø':
          sb.append("O");
          break;
        case 'ø':
          sb.append("o");
          break;
        case 'Œ':
          sb.append("OE");
          break;
        case 'œ':
          sb.append("oe");
          break;
        case 'Ŧ':
          sb.append("T");
          break;
        case 'ŧ':
          sb.append("t");
          break;
        case 'Ł':
          sb.append("L");
          break;
        case 'ł':
          sb.append("l");
          break;
        default:
          sb.append(c);
      }
    }
    return sb.toString();
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
   * Unescapes various unicode escapes if existing:
   * <p>
   * java unicode escape, four hexadecimal digits \ uhhhh
   * <p>
   * octal escape \nnn The octal value nnn, where nnn stands for 1 to 3 digits between ‘0’ and ‘7’. For example, the code for the ASCII ESC
   * (escape) character is ‘\033’.
   * <p>
   * hexadecimal escape \xhh... The hexadecimal value hh, where hh stands for a sequence of hexadecimal digits (‘0’–‘9’, and either ‘A’–‘F’
   * or ‘a’–‘f’).Like the same construct in ISO C, the escape sequence continues until the first nonhexadecimal digit is seen. However,
   * using more than two hexadecimal digits produces undefined results. (The ‘\x’ escape sequence is not allowed in POSIX awk.)
   *
   * @param text string potentially containing unicode escape chars
   * @return the unescaped string
   */
  public static String unescapeUnicodeChars(String text) {
    if (text == null) {
      return null;
    }
    // replace unicode, hexadecimal or octal character encodings by iterating over the chars once
    //
    // java unicode escape, four hexadecimal digits
    // \ uhhhh
    //
    // octal escape
    // \nnn
    // The octal value nnn, where nnn stands for 1 to 3 digits between ‘0’ and ‘7’. For example, the
    // code for the ASCII
    // ESC (escape) character is ‘\033’.
    //
    // hexadecimal escape
    // \xhh...
    // The hexadecimal value hh, where hh stands for a sequence of hexadecimal digits (‘0’–‘9’, and
    // either ‘A’–‘F’ or
    // ‘a’–‘f’).
    // Like the same construct in ISO C, the escape sequence continues until the first
    // nonhexadecimal digit is seen.
    // However, using more than two hexadecimal digits produces undefined results. (The ‘\x’ escape
    // sequence is not allowed
    // in POSIX awk.)
    int i = 0, len = text.length();
    char c;
    StringBuffer sb = new StringBuffer(len);
    while (i < len) {
      c = text.charAt(i++);
      if (c == '\\') {
        if (i < len) {
          c = text.charAt(i++);
          try {
            if (c == 'u' && text.length() >= i + 4) {
              // make sure we have only hexadecimals
              String hex = text.substring(i, i + 4);
              if (HEX.matcher(hex).find()) {
                c = (char) Integer.parseInt(hex, 16);
                i += 4;
              } else {
                throw new NumberFormatException("No hex value: " + hex);
              }
            } else if (c == 'n' && text.length() >= i + 2) {
              // make sure we have only 0-7 digits
              String oct = text.substring(i, i + 2);
              if (OCT.matcher(oct).find()) {
                c = (char) Integer.parseInt(oct, 8);
                i += 2;
              } else {
                throw new NumberFormatException("No octal value: " + oct);
              }
            } else if (c == 'x' && text.length() >= i + 2) {
              // make sure we have only hexadecimals
              String hex = text.substring(i, i + 2);
              if (HEX.matcher(hex).find()) {
                c = (char) Integer.parseInt(hex, 16);
                i += 2;
              } else {
                throw new NumberFormatException("No hex value: " + hex);
              }
            } else if (c == 'r' || c == 'n' || c == 't') {
              // escaped newline or tab. Replace with simple space
              c = ' ';
            } else {
              throw new NumberFormatException("No char escape");
            }
          } catch (NumberFormatException e) {
            // keep original characters including \ if escape sequence was invalid
            // but replace \n with space instead
            if (c == 'n') {
              c = ' ';
            } else {
              c = '\\';
              i--;
            }
          }
        }
      } // fall through: \ escapes itself, quotes any character but u
      sb.append(c);
    }
    return sb.toString();
  }

  /**
   * Tries to decode a UTF8 string only if common UTF8 character combinations are found which are unlikely to be correctly encoded text.
   * E.g. Ã¼ is the German Umlaut ü and indicates we have encoded utf8 text still.
   */
  public static String decodeUtf8Garbage(String text) {
    Pattern UTF8_TEST = Pattern.compile("(Ã¤|Ã¼|Ã¶|Ã\u0084|Ã\u009C|Ã\u0096|" + // äüöÄÜÖ
        "Ã±|Ã¸|Ã§|Ã®|Ã´|Ã»|Ã\u0091|Ã\u0098|Ã\u0087|Ã\u008E|Ã\u0094|Ã\u009B" + // ñøçîôûÑØÇÎÔÛ
        "Ã¡|Ã©|Ã³|Ãº|Ã\u00AD|Ã\u0081|Ã\u0089|Ã\u0093|Ã\u009A|Ã\u008D)" // áéóúíÁÉÓÚÍ
        , Pattern.CASE_INSENSITIVE);
    if (text != null && UTF8_TEST.matcher(text).find()) {
      // typical utf8 combinations found. Try to decode from latin1 to utf8
      byte[] bytes = text.getBytes(Charsets.ISO_8859_1);
      final CharsetDecoder utf8Decoder = Charsets.UTF_8.newDecoder();
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      try {
        return utf8Decoder.decode(buffer).toString();
      } catch (CharacterCodingException e) {
        // maybe wasnt a good idea, return original
      }
    }
    return text;
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
    x = foldToAscii(x);
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
}
