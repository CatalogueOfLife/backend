package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.TaxRelType;
import org.junit.Test;

import java.util.List;

public class TaxRelTypeParserTest extends ParserTestBase<TaxRelType> {

  public TaxRelTypeParserTest() {
    super(TaxRelTypeParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(TaxRelType.EQUALS, "equals");
    assertParse(TaxRelType.EQUALS, "Congruent");
    assertParse(TaxRelType.EQUALS, "EQ");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "3d");
  }
}
