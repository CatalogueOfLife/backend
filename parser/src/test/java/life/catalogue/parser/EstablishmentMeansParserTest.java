package life.catalogue.parser;

import life.catalogue.api.vocab.EstablishmentMeans;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class EstablishmentMeansParserTest extends ParserTestBase<EstablishmentMeans> {

  public EstablishmentMeansParserTest() {
    super(EstablishmentMeansParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(EstablishmentMeans.NATIVE_ENDEMIC, "endemic");
    assertParse(EstablishmentMeans.INTRODUCED, "Alien");
    assertParse(EstablishmentMeans.INTRODUCED, "EXOTIC");
    assertParse(EstablishmentMeans.UNCERTAIN, "N/A");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}