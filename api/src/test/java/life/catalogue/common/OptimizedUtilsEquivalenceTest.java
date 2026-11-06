package life.catalogue.common;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.area.Country;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.common.text.CSVUtils;
import life.catalogue.common.text.StringUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.UnicodeUtils;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.common.reflect.ClassPath;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares the optimized implementations of frequently called utilities with copies of the code they replaced,
 * on edge cases and on many random strings built from the characters each implementation treats specially.
 * The optimizations must not change any result.
 */
public class OptimizedUtilsEquivalenceTest {
  private static final int RUNS = 50_000;
  private final Random rnd = new Random(20261106);

  private String random(String alphabet, int maxLen) {
    int len = rnd.nextInt(maxLen + 1);
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  private <T> void assertSame(Function<String, T> expected, Function<String, T> actual, String alphabet, int maxLen, String... edgeCases) {
    List<String> inputs = new ArrayList<>(Arrays.asList(edgeCases));
    inputs.add(null);
    inputs.add("");
    for (int i = 0; i < RUNS; i++) {
      inputs.add(random(alphabet, maxLen));
    }
    for (String x : inputs) {
      assertEquals(outcome(expected, x), outcome(actual, x), () -> "Different result for >>" + x + "<<");
    }
  }

  /**
   * @return the result or the class and message of the exception thrown, so exceptions are compared too
   */
  private static <T> Object outcome(Function<String, T> func, String x) {
    try {
      return func.apply(x);
    } catch (NullPointerException e) {
      // the helpful NPE messages name the method that was called on null, which is no part of the behavior
      return e.getClass().getName();
    } catch (RuntimeException e) {
      return e.getClass().getName() + ": " + e.getMessage();
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // StringUtils

  private static final Pattern SPACE = Pattern.compile("[\\u00A0\\u2000-\\u200A\\u2028\\u2029\\u202F]");
  private static final Pattern SOFT_HYPHEN_CAPITAL = Pattern.compile("\\u00AD(?=\\p{Lu})");
  private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u061C\\u180E\\u200B-\\u200F\\u202A-\\u202C\\u202F\\u2060-\\u206F\\uFEFF\\uFFF9-\\uFFFB]");

  private static String oldCleanInvisible(String value) {
    if (value == null)
      return value;
    return INVISIBLE.matcher(SOFT_HYPHEN_CAPITAL.matcher(SPACE.matcher(value).replaceAll(" ")).replaceAll("-")).replaceAll("");
  }

  @Test
  public void cleanInvisible() {
    String chars = "aB -\u009F ¡­­A؜᠎  ​‏  ‪ ⁠⁯﻿￹￻é";
    assertSame(OptimizedUtilsEquivalenceTest::oldCleanInvisible, StringUtils::cleanInvisible, chars, 12,
      "Miranda­Ribeiro", "abc", "a b");
  }

  @Test
  public void foldToAscii() {
    assertSame(UnicodeUtils::foldToAscii, StringUtils::foldToAscii, "aZ09 .-'\u007F\u0080éøßæŁſ́ﬁ", 10,
      "Abies alba", "Linnæus", "Mill.");
  }

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private static boolean oldEqualsDigitOrAsciiLettersIgnoreCase(String s1, String s2) {
    s1 = StringUtils.digitOrAsciiLetters(s1);
    s2 = StringUtils.digitOrAsciiLetters(s2);
    if (s1 == null || s2 == null) {
      return Objects.equals(s1, s2);
    }
    return WHITESPACE.matcher(s1).replaceAll("").equalsIgnoreCase(WHITESPACE.matcher(s2).replaceAll(""));
  }

  @Test
  public void equalsDigitOrAsciiLettersIgnoreCase() {
    String chars = "aAbé \t\n.-_1 ";
    for (int i = 0; i < RUNS; i++) {
      String a = rnd.nextInt(20) == 0 ? null : random(chars, 6);
      String b = rnd.nextInt(3) == 0 ? a : random(chars, 6);
      assertEquals(oldEqualsDigitOrAsciiLettersIgnoreCase(a, b), StringUtils.equalsDigitOrAsciiLettersIgnoreCase(a, b), a + " vs " + b);
    }
  }

  private static String oldEscapePgCopy(String text, boolean trim) {
    if(text == null || (trim && text.isEmpty())) {
      return null;
    }
    int stackedSpace = 0;
    int numChars = text.length();
    char ch;
    StringBuffer sb = new StringBuffer();
    for(int i = 0; i < numChars; i++) {
      ch = text.charAt(i);
      if (ch == ' ') {
        stackedSpace++;
        continue;
      }
      if (stackedSpace > 0 && (!trim || sb.length()>0)) {
        while(stackedSpace > 0) {
          sb.append(' ');
          stackedSpace--;
        }
      } else if (trim) {
        // the original moved leading spaces into the first gap of the value, a bug fixed since
        stackedSpace = 0;
      }
      switch(ch) {
        case '\\':  { sb.append("\\\\"); break; }
        case '\b':  { sb.append("\\b"); break; }
        case '\f':  { sb.append("\\f"); break; }
        case '\n':  { sb.append("\\n"); break; }
        case '\r':  { sb.append("\\r"); break; }
        case '\t':  { sb.append("\\t"); break; }
        case '\u000b':  { sb.append("\\v"); break; }
        default: {
          sb.append(ch);
          break;
        }
      }
    }
    if (stackedSpace > 0 && !trim) {
      while(stackedSpace > 0) {
        sb.append(' ');
        stackedSpace--;
      }
    }
    return trim && sb.length() == 0 ? null : sb.toString();
  }

  @Test
  public void escapePgCopy() {
    String chars = "ab  \\\b\f\n\r\t\u000bé ";
    for (boolean trim : new boolean[]{true, false}) {
      assertSame(x -> oldEscapePgCopy(x, trim), x -> StringUtils.escapePgCopy(x, trim), chars, 10,
        "  a b", "a b  ", "   ", "a", " a ");
    }
  }

  private static String oldTabRow(String[] columns) {
    boolean empty = true;
    for (int i = 0; i < columns.length; i++) {
      if (columns[i] != null) {
        empty = false;
        columns[i] = oldEscapePgCopy(columns[i], true);
      }
    }
    if (empty) {
      return "";
    }
    return org.apache.commons.lang3.StringUtils.join(columns, '\t') + "\n";
  }

  @Test
  public void tabWriter() throws Exception {
    String chars = "a b\t\n\\";
    for (int i = 0; i < RUNS / 10; i++) {
      String[] row = new String[1 + rnd.nextInt(5)];
      for (int c = 0; c < row.length; c++) {
        row[c] = rnd.nextInt(4) == 0 ? null : random(chars, 5);
      }
      String expected = oldTabRow(row.clone());
      StringWriter sw = new StringWriter();
      try (TabWriter tw = new TabWriter(sw)) {
        tw.write(row.clone());
      }
      assertEquals(expected, sw.toString(), () -> Arrays.toString(row));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // CSVUtils

  private static List<String> oldParseLine(String line, char delimiter, char quote) {
    List<String> result = new ArrayList<>();
    if (line == null || line.isEmpty()) {
      return result;
    }
    StringBuffer curVal = new StringBuffer();
    boolean inQuotes = false;
    boolean startCollectChar = false;
    for (char ch : line.toCharArray()) {
      if (inQuotes) {
        startCollectChar = true;
        if (ch == quote) {
          inQuotes = false;
        } else {
          curVal.append(ch);
        }
      } else {
        if (ch == quote) {
          inQuotes = true;
          if (startCollectChar) {
            curVal.append(quote);
          }
        } else if (ch == delimiter) {
          result.add(curVal.length()==0 ? null : curVal.toString());
          curVal = new StringBuffer();
          startCollectChar = false;
        } else if (ch == '\r') {
          continue;
        } else if (ch == '\n') {
          break;
        } else {
          curVal.append(ch);
        }
      }
    }
    result.add(curVal.length()==0 ? null : curVal.toString());
    return result;
  }

  @Test
  public void parseLine() {
    assertSame(x -> oldParseLine(x, ',', '"'), CSVUtils::parseLine, "ab,\"\r\n ;", 14, "a,\"b,c\",\"d\"\"e\"");
    assertSame(x -> oldParseLine(x, ';', '\''), x -> CSVUtils.parseLine(x, ';', '\''), "ab,'\r\n ;", 14);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // SciNameNormalizer

  private static final Pattern empty = Pattern.compile("[?!\"'`_-]");
  private static final Pattern punct = Pattern.compile("[,.:;]");
  private static final Pattern white = Pattern.compile("\\s{2,}");

  private static String oldNormalizeWhitespaceAndPunctuation(String s) {
    if (s == null) return null;
    s = punct.matcher(s).replaceAll(" ");
    s = white.matcher(s).replaceAll(" ");
    return s.trim();
  }

  private static String oldNormalizedAscii(String s) {
    if (s == null) return null;
    s = UnicodeUtils.foldToAscii(s);
    s = empty.matcher(s).replaceAll("");
    return oldNormalizeWhitespaceAndPunctuation(s);
  }

  private static String oldNormalize(String s, boolean normMonomials) {
    if (!SciNameNormalizer.hasContent(s)) return "";
    s = oldNormalizedAscii(s);
    s = SciNameNormalizer.removeHybridMarker(s);
    if (s.indexOf(' ') > 2) {
      String[] parts = s.split(" +");
      StringBuilder sb = new StringBuilder();
      if (normMonomials) {
        sb.append(SciNameNormalizer.normalizeEpithet(parts[0]));
      } else {
        sb.append(parts[0]);
      }
      for (int i = 1; i < parts.length; i++) {
        sb.append(" ");
        if (Character.isLowerCase(parts[i].charAt(0))) {
          sb.append(SciNameNormalizer.normalizeEpithet(parts[i]));
        } else {
          sb.append(parts[i]);
        }
      }
      s = sb.toString();
    } else if (normMonomials) {
      s = SciNameNormalizer.normalizeEpithet(s);
    }
    return s.trim();
  }

  private static final String NAME_CHARS = "Aaxeéiuo ×X?!\"'`_-,.:;\t\n\u000B\f\r  æ";

  @Test
  public void sciNameNormalizer() {
    String[] edge = {"Abies alba", "Abies  ×  Alba", "Abies \talba", "a\tb", " a - b ", "Ab., ;c", "Linnæus, 1758", "×Abies", "x Abies alba"};
    assertSame(OptimizedUtilsEquivalenceTest::oldNormalizeWhitespaceAndPunctuation, SciNameNormalizer::normalizeWhitespaceAndPunctuation, NAME_CHARS, 12, edge);
    assertSame(OptimizedUtilsEquivalenceTest::oldNormalizedAscii, SciNameNormalizer::normalizedAscii, NAME_CHARS, 12, edge);
    assertSame(x -> oldNormalize(x, false), SciNameNormalizer::normalize, NAME_CHARS, 16, edge);
    assertSame(x -> oldNormalize(x, true), SciNameNormalizer::normalizeAll, NAME_CHARS, 16, edge);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // AuthorshipNormalizer

  private static Pattern pattern(String name) throws ReflectiveOperationException {
    Field f = AuthorshipNormalizer.class.getDeclaredField(name);
    f.setAccessible(true);
    return (Pattern) f.get(null);
  }

  @Test
  public void authorshipNormalizer() throws Exception {
    final Pattern transliterations = pattern("TRANSLITERATIONS");
    final Pattern punctuation = pattern("PUNCTUATION");
    final Pattern trailingInitials = pattern("TRAILING_INITIALS");
    final Pattern fil = pattern("FIL");
    Function<String, String> old = x -> {
      if (org.apache.commons.lang3.StringUtils.isBlank(x)) {
        return null;
      }
      x = UnicodeUtils.foldToAscii(x);
      x = transliterations.matcher(x).replaceAll("$1");
      x = punctuation.matcher(x).replaceAll(" ");
      x = org.apache.commons.lang3.StringUtils.normalizeSpace(x);
      Matcher m = trailingInitials.matcher(x);
      if (m.find() && !m.group(2).trim().equals("f")) {
        x = m.replaceFirst("$2 $1").trim();
      }
      x = fil.matcher(x).replaceAll("$1 filius");
      if (org.apache.commons.lang3.StringUtils.isBlank(x)) {
        return null;
      }
      return x.toLowerCase();
    };
    assertSame(old, AuthorshipNormalizer::normalize, "smithJ fueöé. ,& ", 14,
      "Smith J", "Smith J f", "Müller", "L. f.", "Smith J ", "de Candolle A P");
  }

  // ---------------------------------------------------------------------------------------------------------------
  // enums

  private static String oldEnumValueName(Enum<?> val) {
    return val.name().toLowerCase().replaceAll("_+", " ");
  }

  private static <T extends Enum<?>> T oldLookup(String name, Class<T> vocab) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    final String normedType = name.toUpperCase().replaceAll("[. _-]", "");
    T[] values = vocab.getEnumConstants();
    if (values != null) {
      for (T val : values) {
        final String normedVal = val.name().toUpperCase().replaceAll("[. _-]", "");
        if (normedType.equals(normedVal)) {
          return val;
        }
      }
    }
    return null;
  }

  @Test
  @SuppressWarnings("unchecked")
  public void enums() throws Exception {
    List<Class<? extends Enum<?>>> enums = new ArrayList<>();
    for (var ci : ClassPath.from(getClass().getClassLoader()).getTopLevelClassesRecursive("life.catalogue.api.vocab")) {
      Class<?> cl = ci.load();
      if (cl.isEnum()) {
        enums.add((Class<? extends Enum<?>>) cl);
      }
    }
    enums.add(Rank.class);
    enums.add(NomCode.class);
    assertTrue(enums.size() > 30, "found only " + enums.size() + " enums");
    for (Class<? extends Enum<?>> cl : enums) {
      for (Enum<?> val : cl.getEnumConstants()) {
        assertEquals(oldEnumValueName(val), PermissiveEnumSerde.enumValueName(val));
        assertEquals(oldEnumValueName(val), VocabularyUtils.toString(val));
        String n = val.name();
        for (String x : List.of(n, n.toLowerCase(), n.replace('_', ' '), n.replace('_', '-'), n.replace('_', '.'),
                                " " + n.toLowerCase() + " ", n + "x", n.substring(0, n.length() - 1), oldEnumValueName(val))) {
          assertEquals(oldLookup(x, cl), VocabularyUtils.lookup(x, cl).orElse(null), x);
        }
      }
      for (String x : new String[]{null, "", " ", "-", "._", "unknown", "x y"}) {
        assertEquals(oldLookup(x, cl), VocabularyUtils.lookup(x, cl).orElse(null), x);
      }
    }
  }

  private static Optional<Country> oldFromIsoCode(String code) {
    if (code != null && !code.isEmpty()) {
      String codeUpper = code.toUpperCase().trim();
      for (Country c : Country.values()) {
        if (codeUpper.equals(c.getIso2LetterCode()) || codeUpper.equals(c.getIso3LetterCode())) {
          return Optional.of(c);
        }
      }
    }
    return Optional.empty();
  }

  @Test
  public void countryFromIsoCode() {
    List<String> codes = new ArrayList<>(Arrays.asList(null, "", " ", "x", "zz", "ZZZ", " de ", "deu"));
    for (Country c : Country.values()) {
      codes.add(c.getIso2LetterCode());
      codes.add(c.getIso3LetterCode());
      if (c.getIso2LetterCode() != null) {
        codes.add(c.getIso2LetterCode().toLowerCase() + " ");
      }
    }
    for (String code : codes) {
      assertEquals(oldFromIsoCode(code), Country.fromIsoCode(code), code);
    }
  }

  @Test
  public void dsidHashCode() {
    List<Integer> keys = Arrays.asList(0, -1, 3, Integer.MAX_VALUE, Integer.MIN_VALUE);
    List<String> ids = Arrays.asList(null, "", "a", "x7", "Abies alba");
    for (Integer k : keys) {
      for (String id : ids) {
        assertEquals(Objects.hash(k, id), new DSIDValue<>(k, id).hashCode());
      }
    }
    assertEquals(Objects.hash(null, null), new DSIDValue<>().hashCode());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // ids

  private static final String SHORT_UUID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-";

  private static String oldShortUuid(UUID uuid, String alphabetStr) {
    char[] alphabet = alphabetStr.toCharArray();
    Arrays.sort(alphabet);
    int alphabetSize = alphabet.length;
    int padToLen = (int) Math.ceil(Math.log(25d) / Math.log(alphabetSize) * 16);
    BigInteger value = new BigInteger(uuid.toString().replaceAll("-", ""), 16);
    BigInteger alphaSize = BigInteger.valueOf(alphabetSize);
    StringBuilder shortUuid = new StringBuilder();
    while (value.compareTo(BigInteger.ZERO) > 0) {
      BigInteger[] fracAndRemainder = value.divideAndRemainder(alphaSize);
      shortUuid.append(alphabet[fracAndRemainder[1].intValue()]);
      value = fracAndRemainder[0];
    }
    if (padToLen > 0) {
      int padding = Math.max(padToLen - shortUuid.length(), 0);
      for (int i = 0; i < padding; i++)
        shortUuid.append(alphabet[0]);
    }
    return shortUuid.toString();
  }

  @Test
  public void shortUuid() {
    List<UUID> uuids = new ArrayList<>(List.of(new UUID(0, 0), new UUID(-1, -1), new UUID(0, 1), new UUID(1, 0),
      new UUID(Long.MIN_VALUE, 0), new UUID(0, Long.MIN_VALUE), new UUID(Long.MAX_VALUE, Long.MAX_VALUE)));
    for (int bit = 0; bit < 64; bit++) {
      uuids.add(new UUID(1L << bit, 0));
      uuids.add(new UUID(0, 1L << bit));
    }
    for (int i = 0; i < RUNS; i++) {
      uuids.add(i % 2 == 0 ? UUID.randomUUID() : new UUID(rnd.nextLong(), rnd.nextLong() >>> rnd.nextInt(64)));
    }
    ShortUUID.Builder hex = new ShortUUID.Builder("0123456789abcdef");
    for (UUID uuid : uuids) {
      assertEquals(oldShortUuid(uuid, SHORT_UUID_ALPHABET), ShortUUID.BUILDER.build(uuid).toString(), uuid::toString);
    }
    for (UUID uuid : uuids.subList(0, 500)) {
      assertEquals(oldShortUuid(uuid, "0123456789abcdef"), hex.build(uuid).toString(), uuid::toString);
    }
  }

  /**
   * The IdConverter as it was, for comparison
   */
  private static class OldIdConverter {
    private final char[] chars;
    private final byte[] values;
    private final int radix;
    private final char prefix;
    private final boolean usePrefix;

    OldIdConverter(String chars, Character prefix) {
      this.usePrefix = prefix != null;
      this.prefix = prefix == null ? '_' : prefix;
      this.chars = chars.toCharArray();
      int max = 0;
      for (char c : this.chars) {
        max = max>c ? max : c;
      }
      values = new byte[max+1];
      byte val = 0;
      for (char c : this.chars) {
        values[c] = val++;
      }
      radix = chars.length();
    }

    String encode(int id) {
      byte[] bytes;
      if (id==0) {
        bytes = new byte[1];
        bytes[0] = 0;
      } else {
        int strLen = (int) Math.ceil(IdConverter.logb(id==Integer.MAX_VALUE ? Integer.MAX_VALUE : id+1, radix));
        bytes = new byte[strLen];
        int idx = strLen;
        while (id > 0) {
          int quot = id % radix;
          bytes[--idx] = (byte) quot;
          id = id / radix;
        }
      }
      StringBuilder sb = new StringBuilder();
      if (usePrefix) {
        sb.append(prefix);
      }
      for (byte idx : bytes) {
        sb.append(chars[idx]);
      }
      return sb.toString();
    }

    int decode(String id) {
      try {
        boolean first = true;
        int num = 0;
        char[] chars = id.toCharArray();
        int length = chars.length-(usePrefix ? 2 : 1);
        int power = (int) Math.pow(radix, length);
        for (char c : chars) {
          if (usePrefix && first) {
            first = false;
            continue;
          }
          if (!org.apache.commons.lang3.ArrayUtils.contains(this.chars, c)) {
            throw new IllegalArgumentException("Unsupported character "+c);
          }
          num += values[c] * power;
          power = power / radix;
        }
        return num;
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Cannot decode invalid ID " + id);
      }
    }
  }

  @Test
  public void idConverter() {
    List<String> alphabets = List.of("0123456789ABCDEF",
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/",
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-",
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
      "23456789ABCDEFGHJKLMNPQRSTUVWXYZ",
      "23456789BCDFGHJKLMNPQRSTVWXYZ");
    for (String alphabet : alphabets) {
      for (Character prefix : new Character[]{null, 'x'}) {
        OldIdConverter old = new OldIdConverter(alphabet, prefix);
        IdConverter conv = new IdConverter(alphabet, prefix);
        int radix = alphabet.length();
        List<Integer> ids = new ArrayList<>(List.of(0, 1, 2, -1, -2, -5, Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MIN_VALUE));
        for (long p = radix; p < Integer.MAX_VALUE; p *= radix) {
          ids.add((int) p - 1);
          ids.add((int) p);
          ids.add((int) p + 1);
        }
        for (int i = 0; i < RUNS; i++) {
          ids.add(rnd.nextInt(Integer.MAX_VALUE) >>> rnd.nextInt(31));
        }
        for (int id : ids) {
          String expected = (String) outcome(i -> old.encode(id), null);
          assertEquals(expected, outcome(i -> conv.encode(id), null), () -> alphabet + " " + prefix + " " + id);
          assertEquals(outcome(old::decode, expected), outcome(conv::decode, expected), expected);
        }
        String chars = alphabet.substring(0, 4) + "é￿ ?" + alphabet.charAt(radix - 1);
        assertSame(old::decode, conv::decode, chars, 8, "x", "xx", "￿");
      }
    }
  }
}
