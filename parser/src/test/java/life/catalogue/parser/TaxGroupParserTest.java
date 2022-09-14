package life.catalogue.parser;

import life.catalogue.api.vocab.TaxGroup;

import org.junit.Test;

public class TaxGroupParserTest extends ParserTestBase<TaxGroup> {

  public TaxGroupParserTest() {
    super(TaxGroupParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(TaxGroup.Arthropods, "Arthropoda");
    assertParse(TaxGroup.Arthropods, "arthropods");
    assertParse(TaxGroup.Animals, "animalia");
    assertParse(null, "anima");

    assertParse(TaxGroup.Birds, "Falconidae");
  }

  @Test
  @Override
  public void testUnparsable() throws Exception {
    // dont do anything
  }

}