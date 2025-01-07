package life.catalogue.parser;

import life.catalogue.common.date.FuzzyDate;

import org.junit.Test;

public class DateParser2Test extends ParserTestBase<FuzzyDate> {

  public DateParser2Test() {
    super(DateParser.PARSER);
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1383
   */
  @Test
  public void iso() throws UnparsableException {
    assertParse(FuzzyDate.of(1999), "1999");
    assertParse(FuzzyDate.of(1999, 1), "1999-01");
    assertParse(FuzzyDate.of(1999, 1,2), "1999-01-02");

    assertParse(FuzzyDate.of(2024, 9,30), "30/09/2024");
  }

}
