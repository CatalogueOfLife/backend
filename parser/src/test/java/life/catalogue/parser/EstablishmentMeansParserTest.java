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

  /**
   * GRIIS values, e.g. for the house sparrow in the contiguous US, dataset 307745
   */
  @Test
  public void griis() throws Exception {
    assertParse(EstablishmentMeans.INTRODUCED, "introduced (alien, exotic, non-native, nonindigenous)");
    assertParse(EstablishmentMeans.INTRODUCED_ASSISTED_COLONISATION, "introduced: assisted colonization");
    assertParse(EstablishmentMeans.INTRODUCED_ASSISTED_COLONISATION, "Introduced (assisted colonisation)");
    assertParse(EstablishmentMeans.INTRODUCED_ASSISTED_COLONISATION,
      "introduced (alien, exotic, non-native, nonindigenous); introduced: assisted colonization");
    assertParse(EstablishmentMeans.INTRODUCED_ASSISTED_COLONISATION, "assisted colonization | introduced");
    assertParse(EstablishmentMeans.INTRODUCED, "introduced; alien");
    assertUnparsable("introduced; native");
    assertUnparsable("introduced; deuter");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}