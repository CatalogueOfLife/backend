package life.catalogue.parser;

import life.catalogue.common.date.FuzzyDate;
import life.catalogue.parser.DateParser.DateStringFilter;
import life.catalogue.parser.DateParser.ParseSpec;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.*;

public class DateParserTest {

  /*
   * These tests yield that, when parsing, you should use "d-M-uuuu" as date pattern rather than "dd-MM-uuuu", because "d-M-uuuu" will match
   * "12-04-2004", but "dd-MM-uuuu" will not match "2-4-2012"
   */
  @Test
  public void test1() throws UnparsableException {
    DateParser parser = simpleParser("d-M-uuuu", LocalDate::from);
    Optional<FuzzyDate> parsed = parser.parse("12-04-2004");
    assertNotNull("01", parsed);
    assertNotNull("02", parsed.get());
    assertFalse("03", parsed.get().isFuzzyDate());
    assertEquals("04", LocalDate.class, parsed.get().getDate().getClass());
    assertEquals("05", parsed.get().toLocalDate(), LocalDate.of(2004, 4, 12));
  }

  @Test
  public void test1b() throws UnparsableException {
    DateParser parser = simpleParser("d-M-uuuu", LocalDate::from);
    Optional<FuzzyDate> parsed = parser.parse("22-7-2004");
    assertNotNull("01", parsed);
    assertNotNull("02", parsed.get());
    assertFalse("03", parsed.get().isFuzzyDate());
    assertEquals("04", LocalDate.class, parsed.get().getDate().getClass());
    assertEquals("05", parsed.get().toLocalDate(), LocalDate.of(2004, 7, 22));
  }

  @Test
  public void test1c() throws UnparsableException {
    DateParser parser = simpleParser("d-M-uu", LocalDate::from);
    Optional<FuzzyDate> parsed = parser.parse("3-03-04");
    assertNotNull("01", parsed);
    assertNotNull("02", parsed.get());
    assertFalse("03", parsed.get().isFuzzyDate());
    assertEquals("04", LocalDate.class, parsed.get().getDate().getClass());
    assertEquals("05", parsed.get().toLocalDate(), LocalDate.of(2004, 3, 3));
  }

  @Test(expected = UnparsableException.class)
  public void test1d() throws UnparsableException {
    DateParser parser = simpleParser("dd-MM-uuuu", LocalDate::from);
    parser.parse("3-5-1904");
  }

  @Test(expected = UnparsableException.class)
  public void test1e() throws UnparsableException {
    DateParser parser = simpleParser("[[d-]M-]uuuu", LocalDate::from);
    parser.parse("3-03-04");
  }

  /*
   * Strangely, "04-2004" does not match "[[dd-]MM-]uuuu", but "2004" does.
   */
  @Test(expected = UnparsableException.class)
  public void test2() throws UnparsableException {
    DateParser parser = simpleParser("[[d-]M-]uuuu", LocalDate::from);
    parser.parse("04-2004");
  }

  @Test
  public void test3() throws UnparsableException {
    DateParser parser = simpleParser("uuuu", Year::from);
    Optional<FuzzyDate> parsed = parser.parse("2004");
    assertNotNull("01", parsed);
    assertNotNull("02", parsed.get());
    assertTrue("03", parsed.get().isFuzzyDate());
    assertEquals("04", Year.class, parsed.get().getDate().getClass());
    assertEquals("05", parsed.get().toLocalDate(), LocalDate.of(2004, 1, 1));
  }

  @Test
  public void test4() throws UnparsableException {
    DateParser parser = simpleParser("uuuu-M", YearMonth::from);
    Optional<FuzzyDate> parsed = parser.parse("2004-04");
    assertNotNull("01", parsed);
    assertNotNull("02", parsed.get());
    assertTrue("03", parsed.get().isFuzzyDate());
    assertEquals("04", YearMonth.class, parsed.get().getDate().getClass());
    assertEquals("05", parsed.get().toLocalDate(), LocalDate.of(2004, 4, 1));
  }

  @Test
  public void test5() throws UnparsableException {
    // 'Z' is just a string literal here!
    DateParser parser = simpleParser("uuuu-M-d'T'HH:mm[:ss]'Z'", LocalDateTime::from);
    Optional<FuzzyDate> parsed = parser.parse("2007-10-13T13:02Z");
    assertNotNull("01", parsed);
    assertNotNull("02", parsed.get());
    assertFalse("03", parsed.get().isFuzzyDate());
    assertEquals("04", LocalDate.class, parsed.get().getDate().getClass());
    assertEquals("05", parsed.get().toLocalDate(), LocalDate.of(2007, 10, 13));
  }

  @Test
  public void test6() throws UnparsableException {
    DateParser parser = simpleParser("uuuu-M-d'T'HH:mm[:ss]'Z'", LocalDateTime::from);
    Optional<FuzzyDate> parsed = parser.parse("2007-10-13T13:02Z");
    assertNotNull("01", parsed);
    assertNotNull("02", parsed.get());
    assertFalse("03", parsed.get().isFuzzyDate());
    assertEquals("04", LocalDate.class, parsed.get().getDate().getClass());
    assertEquals("05", parsed.get().toLocalDate(), LocalDate.of(2007, 10, 13));
  }

  @Test
  public void test7() {
    DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:30+01:00:20", OffsetDateTime::from);
    assertEquals("2011-10-03T10:15:30+01:00:20", ta.toString());
  }

  /*
   * ISO_DATE_TIME also takes care of date strings ending in Z
   */
  @Test
  public void test7b() {
    DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:30Z", OffsetDateTime::from);
    assertEquals("2011-10-03T10:15:30Z", ta.toString());
  }

  @Test
  public void test8() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm[:ss]X");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:22+0100", OffsetDateTime::from);
    assertEquals("2011-10-03T10:15:22+01:00", ta.toString());
  }

  /*
   * uuuu-M-d'T'HH:mm[:ss]X takes care of date strings ending in Z, but also date strings with zone offsets without colon like
   * 2011-10-03T10:15:22+0300
   */
  @Test
  public void test8b() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm[:ss]X");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:22Z", OffsetDateTime::from);
    assertEquals("2011-10-03T10:15:22Z", ta.toString());
  }

  /*
   * Zone offset +0300
   */
  @Test
  public void test8c() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm[:ss]X");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:22+0300", OffsetDateTime::from);
    assertEquals("2011-10-03T10:15:22+03:00", ta.toString());
  }

  /*
   * Zone offset +03; date string without seconds
   */
  @Test
  public void test8d() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm[:ss]X");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:22+03", OffsetDateTime::from);
    assertEquals("2011-10-03T10:15:22+03:00", ta.toString());
  }

  @Test
  public void test8e() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm[:ss]X");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15+03", OffsetDateTime::from);
    assertEquals("2011-10-03T10:15+03:00", ta.toString());
  }

  @Test
  public void test9() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm[:ss]Z");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15+0100", OffsetDateTime::from);
    // Lesson: if seconds not present, then also not printed (not rounded to :00)
    assertEquals("2011-10-03T10:15+01:00", ta.toString());
  }

  @Test
  public void test10() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm:ss'a'");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:18a", LocalDateTime::from);
    assertEquals("2011-10-03T10:15:18", ta.toString());
  }

  @Test
  public void test10b() {
    DateTimeFormatter dtf = formatter("uuuu-M-d'T'HH:mm:ss'a'");
    TemporalAccessor ta = dtf.parse("2011-10-03T10:15:18a", LocalDate::from);
    assertEquals("2011-10-03", ta.toString());
  }

  @Test
  public void test11() {
    DateTimeFormatter dtf = formatter("uuuu[/M]");
    TemporalAccessor ta = dtf.parse("2011/10", Year::from);
    assertEquals("01", Year.class, ta.getClass());
  }

  @Test
  public void test12() throws UnparsableException {
    DateStringFilter filter = new YearExtractor();
    DateTimeFormatter formatter = formatter("uuuu");
    TemporalQuery<?>[] parseInto = new TemporalQuery[] {Year::from};
    List<ParseSpec> parseSpecs = Arrays.asList(new ParseSpec(filter, formatter, parseInto));
    DateParser parser = new DateParser(parseSpecs);
    Optional<FuzzyDate> date = parser.parse("2008a");
    assertTrue("01", date.get().isFuzzyDate());
    assertEquals("02", Year.class, date.get().getDate().getClass());
    assertEquals("03", date.get().toLocalDate(), LocalDate.of(2008, 1, 1));
  }

  ////////////////////////////////////////////////////////////////
  // Tests with the "default" parser (using fuzzy-date.properties)
  ////////////////////////////////////////////////////////////////

  @Test
  public void test100() throws UnparsableException {
    Optional<FuzzyDate> date = DateParser.PARSER.parse("");
    assertNotNull("01", date);
    assertFalse("02", date.isPresent());
  }

  @Test
  public void test101() throws UnparsableException {
    Optional<FuzzyDate> date = DateParser.PARSER.parse(null);
    assertNotNull("01", date);
    assertFalse("02", date.isPresent());
  }

  @Test(expected = UnparsableException.class)
  public void test102() throws UnparsableException {
    DateParser.PARSER.parse("^&*");
  }

  @Test
  public void test103() throws UnparsableException {
    Optional<FuzzyDate> date = DateParser.PARSER.parse("2005");
    assertNotNull("01", date);
    assertEquals("02", Year.class, date.get().getDate().getClass());
    assertEquals("03", LocalDate.of(2005, 01, 01), date.get().toLocalDate());
  }

  @Test
  public void test104() throws UnparsableException {
    DateParser.PARSER.parse("2005-02-29");
    // Default is to parse lenient (no UnparsableException expected)
  }

  @Test
  /**
   * Issue #385
   */
  public void test105() throws UnparsableException {
    Optional<FuzzyDate> date = DateParser.PARSER.parse("05-May-2014");
    assertEquals(5, date.get().toLocalDate().get(ChronoField.DAY_OF_MONTH));
    assertEquals(5, date.get().toLocalDate().get(ChronoField.MONTH_OF_YEAR));
  }

  /**
   * https://github.com/Sp2000/colplus-backend/issues/110
   */
  @Test
  public void test110() throws UnparsableException {
    for (String raw : Lists.newArrayList("1996", "May-1996", "1996/1997")) {
      Optional<FuzzyDate> date = DateParser.PARSER.parse(raw);
      System.out.println(date.get().toLocalDate());
      assertEquals(1996, date.get().toLocalDate().get(ChronoField.YEAR));
    }
  }

  private static DateParser simpleParser(String pattern, TemporalQuery<?> parseInto) {
    List<ParseSpec> parseSpecs = new ArrayList<>(1);
    DateTimeFormatter formatter = formatter(pattern);
    TemporalQuery<?>[] into = new TemporalQuery[] {parseInto};
    parseSpecs.add(new ParseSpec(null, formatter, into));
    return new DateParser(parseSpecs);
  }

  private static DateTimeFormatter formatter(String pattern) {
    return new DateTimeFormatterBuilder().appendPattern(pattern).toFormatter();
  }

}
