package org.col.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

@SuppressWarnings("static-method")
public class YearExtractorTest {

  @Test
  public void test1() {
    assertEquals("01", "2008", new YearExtractor().filter("2008"));
  }

  @Test
  public void test2() {
    assertEquals("01", "2008", new YearExtractor().filter("**2008"));
  }

  @Test
  public void test3() {
    assertEquals("01", "2008", new YearExtractor().filter(" 2008"));
  }

  @Test
  public void test4() {
    assertEquals("01", "2008", new YearExtractor().filter(" 2008"));
  }

  @Test
  public void test5() {
    assertEquals("01", "2008", new YearExtractor().filter("2008 "));
  }

  @Test
  public void test6() {
    assertEquals("01", "2008", new YearExtractor().filter("2008b"));
  }

  @Test
  public void test7() {
    assertEquals("01", "2008", new YearExtractor().filter("a2008"));
  }

  @Test
  public void test8() {
    assertNull("01", new YearExtractor().filter("2008199"));
  }

  @Test
  public void test9() {
    assertEquals("01", "2008", new YearExtractor().filter(" 2008 - 2012"));
  }

  @Test
  public void test10() {
    assertEquals("01", "2012", new YearExtractor().filter(" 200 - 2012"));
  }

  @Test
  public void test11() {
    assertEquals("01", "2018", new YearExtractor()
        .filter("2018 [Code compliant e-publication published 7 November 2017]"));
  }
}
