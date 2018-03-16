package org.col.admin.task.importer.acef;

import org.col.parser.UnparsableException;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("static-method")
public class AcefDateParserTest {
  AcefDateParser parser = AcefDateParser.PARSER;

  @Test
  public void testEmpty() throws Exception {
    assertEquals(Optional.empty(), parser.parse(""));
    assertEquals(Optional.empty(), parser.parse(null));
    assertEquals(Optional.empty(), parser.parse(" "));
    assertEquals(Optional.empty(), parser.parse("   "));
  }

  void assertUnparsable(String x) throws UnparsableException {
    try {
      Optional<?> val = parser.parse(x);
      // we should never reach here
      fail("Expected "+x+" to be unparsable but was "+ (val.isPresent() ? val.get() : "EMPTY"));
    } catch (UnparsableException e) {
      // expected
    }
  }

  @Test
  public void testUnparsable() throws Exception {
    for (String x : new String[]{"321", "jan", "31-01"}) {
      assertUnparsable(x);
    }
  }

  @Test(expected = UnparsableException.class)
  public void parseYear1() throws Exception {
    String s = "1972a";
    parser.parse(s);
  }

  @Test(expected = UnparsableException.class)
  public void parseYear2() throws Exception {
    String s = "19727";
    parser.parse(s);
  }

  @Test(expected = UnparsableException.class)
  public void parseYear3() throws Exception {
    String s = "27";
    parser.parse(s);
  }

  @Test
  public void parseYear4() throws Exception {
    String s = "1927";
    Optional<LocalDate> date = parser.parse(s);
    assertEquals("01", 1927, date.get().getYear());
  }

}
