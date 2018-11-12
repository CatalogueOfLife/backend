package org.col.admin.importer.acef;

import java.time.LocalDate;
import java.util.Optional;

import org.col.common.date.FuzzyDate;
import org.col.parser.AcefDateParser;
import org.col.parser.UnparsableException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("static-method")
public class AcefDateParserTest {
  
  public void test1() throws UnparsableException {
    Optional<FuzzyDate> fd = AcefDateParser.PARSER.parse("30-Sep-2011");
    assertTrue("01", fd.isPresent());
    assertEquals("02", LocalDate.of(2011, 9, 30));
  }
  
  public void test2() throws UnparsableException {
    Optional<FuzzyDate> fd = AcefDateParser.PARSER.parse("31-Jan-2017 18:49:35");
    assertTrue("01", fd.isPresent());
    assertEquals("02", LocalDate.of(2017, 1, 31));
  }
  
  public void test3() throws UnparsableException {
    Optional<FuzzyDate> fd = AcefDateParser.PARSER.parse("Nov-2010");
    assertTrue("01", fd.isPresent());
    assertEquals("02", LocalDate.of(2010, 11, 01));
  }
  
  public void test4() throws UnparsableException {
    Optional<FuzzyDate> fd = AcefDateParser.PARSER.parse("29/11/2002");
    assertTrue("01", fd.isPresent());
    assertEquals("02", LocalDate.of(2002, 11, 29));
  }
}
