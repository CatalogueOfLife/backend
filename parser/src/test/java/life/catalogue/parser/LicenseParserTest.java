package life.catalogue.parser;

import life.catalogue.api.vocab.License;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import static life.catalogue.api.vocab.License.*;

public class LicenseParserTest extends ParserTestBase<License> {

  public LicenseParserTest() {
    super(LicenseParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(CC0, "cc0");
    assertParse(CC0, "cc zero");
    assertParse(CC0, "publicdomain");

    assertParse(CC_BY, "CCBY");
    assertParse(CC_BY, "CC-BY");
    assertParse(CC_BY, "https://creativecommons.org/licenses/by/3.0/");
    assertParse(CC_BY, "http://creativecommons.org/licenses/by/3.0/");
    assertParse(CC_BY, "http://creativecommons.org/licenses/by/4.0/");

    assertParse(CC_BY_NC, "ccbync");
    assertParse(CC_BY_NC, "cc-by-nc");
    assertParse(CC_BY_NC, "http://creativecommons.org/licenses/by-nc/4.0/");
    assertParse(CC_BY_NC, "http://creativecommons.org/licenses/by-nc/1.0/");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter", "unknown");
  }
}