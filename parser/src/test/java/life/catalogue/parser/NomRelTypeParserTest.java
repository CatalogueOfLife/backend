package life.catalogue.parser;

import life.catalogue.api.vocab.NomRelType;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class NomRelTypeParserTest extends ParserTestBase<NomRelType> {

  public NomRelTypeParserTest() {
    super(NomRelTypeParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(NomRelType.BASIONYM, "hasBasionym");
    assertParse(NomRelType.SPELLING_CORRECTION, "EMENDATION");
    assertParse(NomRelType.SPELLING_CORRECTION, "Orthographic variant");
    assertParse(NomRelType.SPELLING_CORRECTION, "Orth var");
    assertParse(NomRelType.HOMOTYPIC, "objective synonym");
    assertParse(NomRelType.HOMOTYPIC, "nomenclatural-synonym");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
