package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.NomRelType;
import org.junit.Test;

public class NomRelTypeParserTest extends ParserTestBase<NomRelType> {

  public NomRelTypeParserTest() {
    super(NomRelTypeParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(NomRelType.BASIONYM, "hasBasionym");
    assertParse(NomRelType.SPELLING_CORRECTION, "emendation");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
