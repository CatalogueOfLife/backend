package org.col.parser;

import com.google.common.collect.Lists;
import org.gbif.nameparser.api.NomCode;
import org.junit.Test;

import java.util.List;

/**
 *
 */
public class NomCodeParserTest extends ParserTestBase<NomCode> {

  public NomCodeParserTest() {
    super(NomCodeParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(NomCode.BOTANICAL, "botany");
    assertParse(NomCode.BOTANICAL, "plants");
    assertParse(NomCode.BOTANICAL, "ICBN");
    assertParse(NomCode.BOTANICAL, "ICNafp");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
