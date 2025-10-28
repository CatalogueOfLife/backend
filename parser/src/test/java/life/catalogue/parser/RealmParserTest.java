package life.catalogue.parser;

import life.catalogue.api.vocab.BioGeoRealm;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class RealmParserTest extends ParserTestBase<BioGeoRealm> {

  public RealmParserTest() {
    super(RealmParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(BioGeoRealm.Neotropic, "neotropic");
    assertParse(BioGeoRealm.Neotropic, "Neotropic");
    assertParse(BioGeoRealm.Neotropic, "NEOTROPIC");

    assertParse(BioGeoRealm.Neotropic, "South america");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter", "unknown");
  }
}