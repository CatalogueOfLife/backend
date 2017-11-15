package org.col.parser;

import com.google.common.collect.Lists;
import org.col.api.vocab.Gazetteer;
import org.junit.Test;

import java.util.List;

/**
 *
 */
public class AreaParserTest extends ParserTestBase<AreaParser.Area> {

  public AreaParserTest() {
    super(AreaParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(new AreaParser.Area("AGS", Gazetteer.TDWG), "tdwg:AGS");
    assertParse(new AreaParser.Area("AGS", Gazetteer.TDWG), "TDWG:ags ");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList(".", "?", "---", "öüä", "#67#", "wtf", "nothing");
  }

  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("t ru e", "a", "2", "Nig", "har:123");
  }

}