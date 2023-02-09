package life.catalogue.parser;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

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
    // plural
    assertParse(Rank.GENUS, "genera");
    assertParse(Rank.PROLE, "prole");
    assertParse(Rank.PROLE, "proles");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }

}