package life.catalogue.parser;

import life.catalogue.api.vocab.EstablishmentMeans;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class DegreeOfEstablishmentParserTest extends ParserTestBase<EstablishmentMeans> {

  public DegreeOfEstablishmentParserTest() {
    super(EstablishmentMeansParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(EstablishmentMeans.NATIVE, "native");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp", "NOMEN", "NOMEN_000029045634");
  }
}
