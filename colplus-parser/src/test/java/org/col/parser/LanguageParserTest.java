package org.col.parser;

import com.google.common.collect.Lists;
import org.col.api.vocab.Language;
import org.junit.Test;

import java.util.List;

/**
 *
 */
public class LanguageParserTest extends ParserTestBase<Language> {

  public LanguageParserTest() {
    super(LanguageParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(Language.GERMAN, "de");
    assertParse(Language.GERMAN, "deu");
    assertParse(Language.GERMAN, "deutsch");
    assertParse(Language.GERMAN, "GER");

    assertUnparsable("unknown");
    assertUnparsable("zz");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}