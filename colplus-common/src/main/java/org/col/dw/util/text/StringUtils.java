package org.col.dw.util.text;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.text.Normalizer;
import java.util.regex.Pattern;


/**
 * Utils class adding specific string methods to existing guava {@link Strings} and
 * commons {@link org.apache.commons.lang3.StringUtils}.
 */
public class StringUtils {
  private static Pattern MARKER = Pattern.compile("\\p{M}");
  private static final Pattern OCT = Pattern.compile("^[0-7]+$");
  private static final Pattern HEX = Pattern.compile("^[0-9abcdefABCDEF]+$");

  private StringUtils() {
  }

  /**
   * Removes accents & diacretics and converts ligatures into several chars
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
   * Increase a given string by 1, i.e. increase the last char in that string by one.
   * If its a z or Z the char before is increased instead and a new char a is appended.
   * Only true letters are increased, but spaces, punctuation or numbers remain unchanged.
   * Null values stay null and empty strings empty.
   * The case of existing characters will be kept and the appended chars will use the case of the last char of the
   * original string.
   *
   * For example "Carlb" becomes "Carla", "Aua" "Atz", "zZz" "aAaa" or "Abies zzz" "Abiet aaa".
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

    while (idx >= 0){
      char c = chars[idx];
      if (!Character.isLetter(c)){
        idx--;
        continue;
      }

      if (lastOriginalChar == null){
        lastOriginalChar = c;
      }

      if (c == 'z'){
        chars[idx] = 'a';
        appendingNeeded = true;

      } else if (c == 'Z'){
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
    if (appendingNeeded){
      char append = (lastOriginalChar==null || Character.isLowerCase(lastOriginalChar)) ? 'a' : 'A';
      return String.valueOf(chars) + append;

    } else {
      return String.valueOf(chars);
    }
  }

   /**
   * Unescapes various unicode escapes if existing:
   *
   * java unicode escape, four hexadecimal digits
   * \ uhhhh
   *
   * octal escape
   * \nnn
   * The octal value nnn, where nnn stands for 1 to 3 digits between ‘0’ and ‘7’. For example, the code for the ASCII
   * ESC (escape) character is ‘\033’.
   *
   * hexadecimal escape
   * \xhh...
   * The hexadecimal value hh, where hh stands for a sequence of hexadecimal digits (‘0’–‘9’, and either ‘A’–‘F’ or
   * ‘a’–‘f’).Like the same construct in ISO C, the escape sequence continues until the first nonhexadecimal digit is seen.
   * However, using more than two hexadecimal digits produces undefined results. (The ‘\x’ escape sequence is not allowed
   * in POSIX awk.)
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
    // The octal value nnn, where nnn stands for 1 to 3 digits between ‘0’ and ‘7’. For example, the code for the ASCII
    // ESC (escape) character is ‘\033’.
    //
    // hexadecimal escape
    // \xhh...
    // The hexadecimal value hh, where hh stands for a sequence of hexadecimal digits (‘0’–‘9’, and either ‘A’–‘F’ or
    // ‘a’–‘f’).
    // Like the same construct in ISO C, the escape sequence continues until the first nonhexadecimal digit is seen.
    // However, using more than two hexadecimal digits produces undefined results. (The ‘\x’ escape sequence is not allowed
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
        "Ã±|Ã¸|Ã§|Ã®|Ã´|Ã»|Ã\u0091|Ã\u0098|Ã\u0087|Ã\u008E|Ã\u0094|Ã\u009B"  + // ñøçîôûÑØÇÎÔÛ
        "Ã¡|Ã©|Ã³|Ãº|Ã\u00AD|Ã\u0081|Ã\u0089|Ã\u0093|Ã\u009A|Ã\u008D)"         // áéóúíÁÉÓÚÍ
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
   * Generic string cleaning that replaces all non digit or letter characters with a single space and finally trims to null;
   * replaces all non digit or letter characters with a single space, including invisible control characters, underscore and dashes;
   * converts the string to upper case;
   * finally folds the string to pure ASCII characters
   *
   * @param x any input string used for parsing a single value
   * @return the cleaned, normalized string
   */
  public static String digitOrAsciiLetters(String x) {
    CharMatcher cm = CharMatcher.javaLetterOrDigit().negate();
    if (x == null) return null;
    x = cm.trimAndCollapseFrom(x, ' ');
    x = x.toUpperCase();
    x = foldToAscii(x);
    return Strings.emptyToNull(x);
  }
}
