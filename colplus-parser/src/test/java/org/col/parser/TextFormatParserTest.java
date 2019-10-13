package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.TextFormat;
import org.junit.Test;

public class TextFormatParserTest extends ParserTestBase<TextFormat> {
  
  public TextFormatParserTest() {
    super(TextFormatParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse(TextFormat.PLAIN_TEXT, "text");
    assertParse(TextFormat.PLAIN_TEXT, "plain_text");
    assertParse(TextFormat.HTML, "html");
    assertParse(TextFormat.HTML, "text/html");
    assertParse(TextFormat.MARKDOWN, "md");
  }
  
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}