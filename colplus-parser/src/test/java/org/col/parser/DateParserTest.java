package org.col.parser;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/**
 *
 */
public class DateParserTest extends ParserTestBase<LocalDate> {

  public DateParserTest() {
    super(DateParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(LocalDate.of(1999, 02, 21), "1999-02-21");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}