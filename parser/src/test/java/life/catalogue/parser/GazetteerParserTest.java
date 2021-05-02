package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.Gazetteer;
import org.junit.Test;

import java.util.List;

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