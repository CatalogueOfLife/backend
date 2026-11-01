package life.catalogue.parser;

import life.catalogue.api.vocab.GeoTime;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class GeoTimeParserTest extends ParserTestBase<GeoTime> {
  
  public GeoTimeParserTest() {
    super(GeoTimeParser.PARSER);
  }
  
  @Test
  public void
  parse() throws Exception {
    assertParse("Aalenian", "aalenian");
    assertParse("Aalenian", "AALENIAN");
    assertParse("Aalenian", "Aalenium");
    assertParse("Aalenian", "Aalenian Age");
    assertParse("Aalenian", "Aalénium");
    assertParse("Aalenian", "пален");
  
    assertParse("LowerDevonian", "Early Devonian");
    assertParse("LowerDevonian", "Lower Devonian");
    assertParse("LowerDevonian", "Lower_Devonian");

    assertParse("Cambrian", "Cambrian");

    assertParse("LowerCretaceous", "Apatinė Kreida");
    assertParse("UpperCretaceous", "yngre krita");
    assertParse("UpperCretaceous", "Crétacé supérieur");
    assertParse("UpperCretaceous", "Späte Kreide");
    assertParse("UpperCretaceous", "Oberkreide");

    // the name in the 2020 chart
    assertParse("Chibanian", "MiddlePleistocene");
    assertParse("Chibanian", "Middle Pleistocene");
    assertParse("Chibanian", "Chibanian");
    // also a label of Archean in the 2026 chart
    assertParse("Hadean", "Hadeano");

    assertParse("Burdigalian", "17Ma");
    assertParse("Burdigalian", "17 Ma");
    assertParse("Burdigalian", "17.1 Ma");

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