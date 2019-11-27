package life.catalogue.parser;

import java.util.List;

import com.google.common.collect.Lists;
import life.catalogue.parser.RankParser;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

/**
 *
 */
public class RankParserTest extends ParserTestBase<Rank> {

  public RankParserTest() {
    super(RankParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(Rank.SPECIES, "species");
    assertParse(Rank.SPECIES, "sp.");
    assertParse(Rank.SUBSPECIES, "subspecies");
    assertParse(Rank.SUBSPECIES, "subsp.");
    assertParse(Rank.SUBSPECIES, "subsp ");
    assertParse(Rank.SUBSPECIES, "ssp ");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }

}