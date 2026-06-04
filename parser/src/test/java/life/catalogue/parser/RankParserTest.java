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

    // ambiguous series (split by code like sections in name-parser 3.16)
    assertParse(Rank.SERIES_BOTANY, "Series");
    assertParse(Rank.SERIES_BOTANY, "ser.");
    assertParse(Rank.SUPERSERIES_BOTANY, "Superseries");
    assertParse(Rank.SUBSERIES_BOTANY, "Subseries");

    assertEquals(Optional.of(Rank.SERIES_BOTANY), RankParser.PARSER.parse(NomCode.BOTANICAL,"Series"));
    assertEquals(Optional.of(Rank.SERIES_ZOOLOGY), RankParser.PARSER.parse(NomCode.ZOOLOGICAL,"Series"));
    assertEquals(Optional.of(Rank.SUPERSERIES_ZOOLOGY), RankParser.PARSER.parse(NomCode.ZOOLOGICAL,"SUPERSeries"));
    assertEquals(Optional.of(Rank.SUBSERIES_ZOOLOGY), RankParser.PARSER.parse(NomCode.ZOOLOGICAL,"SUBSeries"));
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }

}