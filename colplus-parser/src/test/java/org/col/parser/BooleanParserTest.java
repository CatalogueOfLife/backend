package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

/**
 *
 */
public class BooleanParserTest extends ParserTestBase<Boolean> {

  public BooleanParserTest() {
    super(BooleanParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(true, "true");
    assertParse(true, "yes");
    assertParse(true, " t    ");
    assertParse(true, "T");
    assertParse(true, "si");
    assertParse(true, "ja");
    assertParse(true, "oui");
    assertParse(true, "wahr");

    assertParse(false, "f");
    assertParse(false, "f");
    assertParse(false, "no");
    assertParse(false, "nein");
    assertParse(false, "-1");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("t ru e", "a", "2");
  }

}