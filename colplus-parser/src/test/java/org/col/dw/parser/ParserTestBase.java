package org.col.dw.parser;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 */
abstract class ParserTestBase<T> {
  final Parser<T> parser;

  public ParserTestBase(Parser<T> parser) {
    this.parser = parser;
  }

  void assertParse(T expected, String input) throws UnparsableException {
    assertEquals(Optional.of(expected), parser.parse(input));
  }

  @Test
  public void testEmpty() throws Exception {
    assertEquals(Optional.empty(), parser.parse(""));
    assertEquals(Optional.empty(), parser.parse(null));
    assertEquals(Optional.empty(), parser.parse(" "));
    assertEquals(Optional.empty(), parser.parse("   "));
  }

  @Test
  public void testUnparsable() throws Exception {
    List<String> values = unparsableValues();
    values.addAll(additionalUnparsableValues());

    for (String x : values) {
      try {
        Optional<T> val = parser.parse(x);
        // we should never reach here
        fail("Expected "+x+" to be unparsable but was "+ (val.isPresent() ? val.get() : "EMPTY"));
      } catch (UnparsableException e) {
        // expected
      }
    }
  }

  /**
   * Override to test specific unparsable values
   */
  List<String> unparsableValues() {
    return Lists.newArrayList(".", "?", "---", "öüä", "#67#", "wtf", "nothing");
  }

  /**
   * Override to add more specific unparsable values to the base list above
   */
  List<String> additionalUnparsableValues() {
    return Collections.EMPTY_LIST;
  }

}