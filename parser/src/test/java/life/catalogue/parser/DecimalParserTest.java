package life.catalogue.parser;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class DecimalParserTest extends ParserTestBase<Double> {

  public DecimalParserTest() {
    super(DecimalParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(23.0,"23");
    assertParse(23.567,"23.567");
    assertParse(13323.567,"13 ,323 .567");
    assertParse(-23.567," - 23,567");
    assertParse(23.12567,"23,12567");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("t ur e", "a321", "267;78");
  }

}
