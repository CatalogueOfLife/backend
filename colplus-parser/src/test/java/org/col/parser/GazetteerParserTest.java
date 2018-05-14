package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.Gazetteer;
import org.junit.Test;

/**
 *
 */
public class GazetteerParserTest extends ParserTestBase<Gazetteer> {

  public GazetteerParserTest() {
    super(GazetteerParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(Gazetteer.TDWG, "level1");
    assertParse(Gazetteer.TDWG, "tdwg1");
    assertParse(Gazetteer.TDWG, "tdwg");
    assertParse(Gazetteer.ISO, "Iso");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}