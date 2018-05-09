package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.NomStatus;
import org.junit.Test;

/**
 *
 */
public class NomStatusParserTest extends ParserTestBase<NomStatus> {

  public NomStatusParserTest() {
    super(NomStatusParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(NomStatus.MANUSCRIPT, "ms");
    assertParse(NomStatus.MANUSCRIPT, "manuscript");
    assertParse(NomStatus.MANUSCRIPT, "ined.");
    assertParse(NomStatus.MANUSCRIPT, "ineditus");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
