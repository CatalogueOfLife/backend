package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

/**
 *
 */
public class LanguageParserTest extends ParserTestBase<String> {

  public LanguageParserTest() {
    super(LanguageParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse("deu", "de");
    assertParse("deu", "deu");
    assertParse("deu", "german");
    assertParse("deu", "deutsch");
    assertParse("deu", "GER");

    assertUnparsable("unknown");
    assertUnparsable("zz");
  }


  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}