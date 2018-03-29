package org.col.parser;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 *
 */
abstract class EnumNoteParserTestBase<T extends Enum> extends ParserTestBase<EnumNote<T>> {

  public EnumNoteParserTestBase(Parser<EnumNote<T>> parser) {
    super(parser);
  }

  void assertParse(T expected, String note, String input) throws UnparsableException {
    assertEquals(Optional.of(new EnumNote<>(expected, note)), parser.parse(input));
  }

  void assertParse(T expected, String input) throws UnparsableException {
    assertParse(expected, null, input);
  }
}