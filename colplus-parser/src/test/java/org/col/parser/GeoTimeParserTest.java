package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.GeoTime;
import org.junit.Test;

public class GeoTimeParserTest extends ParserTestBase<GeoTime> {
  
  public GeoTimeParserTest() {
    super(GeoTimeParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse("Aalenian", "aalenian");
    assertParse("Aalenian", "AALENIAN");
    assertParse("Aalenian", "Aalenium");
    assertParse("Aalenian", "Aalenian Age");
    assertParse("Aalenian", "Aalénium");
    assertParse("Aalenian", "пален");
    
    assertUnparsable("unknown");
    assertUnparsable("zz");
  }
  
  private void assertParse(String expected, String input) throws UnparsableException {
    assertParse(GeoTime.byName(expected), input);
  }
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}