package life.catalogue.parser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.assertEquals;

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
    assertParse(Rank.PROLES, "prole");
    assertParse(Rank.PROLES, "proles");
    // ambiguous sections
    assertParse(Rank.SECTION_BOTANY, "Section");
    assertParse(Rank.SUPERSECTION_BOTANY, "Super Section");
    assertParse(Rank.SUBSECTION_BOTANY, "Subsection");

    assertEquals(Optional.of(Rank.SECTION_BOTANY), RankParser.PARSER.parse(NomCode.BOTANICAL,"Section"));
    assertEquals(Optional.of(Rank.SECTION_ZOOLOGY), RankParser.PARSER.parse(NomCode.ZOOLOGICAL,"Section"));
    assertEquals(Optional.of(Rank.SUPERSECTION_ZOOLOGY), RankParser.PARSER.parse(NomCode.ZOOLOGICAL,"SUPERSection"));
    assertEquals(Optional.of(Rank.SUBSECTION_ZOOLOGY), RankParser.PARSER.parse(NomCode.ZOOLOGICAL,"SUBSection"));
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }

}