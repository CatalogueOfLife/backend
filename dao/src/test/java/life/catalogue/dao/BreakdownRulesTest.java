package life.catalogue.dao;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.gbif.nameparser.api.Rank.*;
import static org.junit.Assert.*;

public class BreakdownRulesTest {

  @Test
  public void countsSpeciesByDefault() {
    assertEquals(SPECIES, BreakdownRules.countRank(Map.of(FAMILY, 17, GENUS, 131, SPECIES, 301)));
  }

  @Test
  public void countsGeneraWhenTheyOutnumberSpeciesTwofold() {
    // IRMNG Arthropoda, #1584: 126k genera and 2 species
    assertEquals(GENUS, BreakdownRules.countRank(Map.of(CLASS, 30, ORDER, 300, GENUS, 126_000, SPECIES, 2)));
    assertEquals(GENUS, BreakdownRules.countRank(Map.of(GENUS, 5)));
    // exactly twice as many genera is still species
    assertEquals(SPECIES, BreakdownRules.countRank(Map.of(GENUS, 20, SPECIES, 10)));
  }

  @Test
  public void nothingToCount() {
    assertNull(BreakdownRules.countRank(Map.of(FAMILY, 3)));
    assertNull(BreakdownRules.countRank(Map.of()));
  }

  @Test
  public void twoRingsOfTheNextLinneanRanks() {
    var counts = Map.of(ORDER, 1, FAMILY, 17, GENUS, 131, SPECIES, 301);
    assertEquals(List.of(FAMILY, GENUS), BreakdownRules.ringRanks(ORDER, counts, SPECIES));
  }

  @Test
  public void emptyRanksAreSkipped() {
    // a phylum whose subtree has no classes: orders are the first ring, families the second
    var counts = Map.of(PHYLUM, 1, ORDER, 4, FAMILY, 20, SPECIES, 90);
    assertEquals(List.of(ORDER, FAMILY), BreakdownRules.ringRanks(PHYLUM, counts, SPECIES));
    // and no genera either: the second ring is species, which is the count rank, so there is only one ring
    counts = Map.of(PHYLUM, 1, ORDER, 4, SPECIES, 90);
    assertEquals(List.of(ORDER), BreakdownRules.ringRanks(PHYLUM, counts, SPECIES));
  }

  @Test
  public void secondRingStaysAboveTheCountRank() {
    // counting genera: families ring, no genus ring
    var counts = Map.of(ORDER, 1, FAMILY, 17, GENUS, 131, SPECIES, 3);
    assertEquals(List.of(FAMILY), BreakdownRules.ringRanks(ORDER, counts, GENUS));
    // a family: genera ring only, species would be the count rank
    counts = Map.of(FAMILY, 1, GENUS, 12, SPECIES, 80);
    assertEquals(List.of(GENUS), BreakdownRules.ringRanks(FAMILY, counts, SPECIES));
  }

  @Test
  public void firstRingMayBeTheCountRankItself() {
    // an order holding nothing but species
    var counts = Map.of(ORDER, 1, SPECIES, 3);
    assertEquals(List.of(SPECIES), BreakdownRules.ringRanks(ORDER, counts, SPECIES));
  }

  @Test
  public void tooManyGrandchildrenMeansOneRing() {
    var counts = Map.of(CLASS, 1, ORDER, 30, FAMILY, BreakdownRules.MAX_GRAND_CHILDREN + 1, SPECIES, 100_000);
    assertEquals(List.of(ORDER), BreakdownRules.ringRanks(CLASS, counts, SPECIES));
    counts = Map.of(CLASS, 1, ORDER, 30, FAMILY, BreakdownRules.MAX_GRAND_CHILDREN, SPECIES, 100_000);
    assertEquals(List.of(ORDER, FAMILY), BreakdownRules.ringRanks(CLASS, counts, SPECIES));
  }

  @Test
  public void nonLinneanTaxonRankStartsAtTheNextLinneanRank() {
    var counts = Map.of(SUBORDER, 1, FAMILY, 5, GENUS, 40, SPECIES, 200);
    assertEquals(List.of(FAMILY, GENUS), BreakdownRules.ringRanks(SUBORDER, counts, SPECIES));
    counts = Map.of(SUPERFAMILY, 1, FAMILY, 5, GENUS, 40, SPECIES, 200);
    assertEquals(List.of(FAMILY, GENUS), BreakdownRules.ringRanks(SUPERFAMILY, counts, SPECIES));
  }

  @Test
  public void noCountRankNoRings() {
    assertEquals(List.of(), BreakdownRules.ringRanks(ORDER, Map.of(ORDER, 1, FAMILY, 3), null));
  }
}
