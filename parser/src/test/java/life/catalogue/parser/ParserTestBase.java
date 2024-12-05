package life.catalogue.parser;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.*;

/**
 *
 */
abstract class ParserTestBase<T> {
  final Parser<T> parser;
  
  public ParserTestBase(Parser<T> parser) {
    this.parser = parser;
  }

  <X extends T> void assertParse(X expected, String input) throws UnparsableException {
    assertEquals(Optional.ofNullable(expected), parser.parse(input));
  }
  
  void assertUnparsable(String x) throws UnparsableException {
    try {
      Optional<? extends T> val = parser.parse(x);
      // we should never reach here
      fail("Expected " + x + " to be unparsable but was " + (val.isPresent() ? val.get() : "EMPTY"));
    } catch (UnparsableException e) {
      // expected
    }
  }

  void assertEmpty(String x) throws UnparsableException {
    assertTrue(parser.parse(x).isEmpty());
  }
  
  @Test
  public void testEmpty() throws Exception {
    assertEmpty("");
    assertEmpty(null);
    assertEmpty(" ");
    assertEmpty("   ");
  }
  
  @Test
  public void testUnparsable() throws Exception {
    List<String> values = unparsableValues();
    values.addAll(additionalUnparsableValues());
    
    for (String x : values) {
      assertUnparsable(x);
    }
  }
  
  /**
   * Override to test specific unparsable values
   */
  List<String> unparsableValues() {
    return Lists.newArrayList(".", "?", "---", "öüäi", "#67#", "wtff", "nothing");
  }
  
  /**
   * Override to add more specific unparsable values to the base list above
   */
  List<String> additionalUnparsableValues() {
    return Collections.EMPTY_LIST;
  }
  
}