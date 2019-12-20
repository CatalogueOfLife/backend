package life.catalogue.parser;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.List;

public class IntegerParserTest extends ParserTestBase<Integer> {

  public IntegerParserTest() {
    super(IntegerParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(23,"23");
    assertParse(2312567,"2312567");
    assertParse(-2312567," - 2312567");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("t ur e", "a321", "267;78", "23.567", "23,567");
  }

}
