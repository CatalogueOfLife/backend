package life.catalogue.common.tax;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class RankUtilsTest {

  @Test
  public void testNextLowerLinneanRank() throws Exception {
    assertEquals(Rank.SPECIES, RankUtils.nextLowerLinneanRank(Rank.GENUS));
    assertEquals(Rank.GENUS, RankUtils.nextLowerLinneanRank(Rank.SUBFAMILY));
    assertEquals(Rank.SPECIES, RankUtils.nextLowerLinneanRank(Rank.SUBGENUS));
    assertEquals(Rank.PHYLUM, RankUtils.nextLowerLinneanRank(Rank.KINGDOM));
    assertEquals(Rank.KINGDOM, RankUtils.nextLowerLinneanRank(Rank.DOMAIN));
    assertEquals(Rank.SPECIES, RankUtils.nextLowerLinneanRank(Rank.INFRAGENERIC_NAME));
    assertEquals(null, RankUtils.nextLowerLinneanRank(Rank.INFRASUBSPECIFIC_NAME));
    assertEquals(null, RankUtils.nextLowerLinneanRank(Rank.VARIETY));
  }

  @Test
  public void testNextHigherLinneanRank() throws Exception {
    assertEquals(Rank.FAMILY, RankUtils.nextHigherLinneanRank(Rank.GENUS));
    assertEquals(Rank.FAMILY, RankUtils.nextHigherLinneanRank(Rank.SUBFAMILY));
    assertEquals(Rank.GENUS, RankUtils.nextHigherLinneanRank(Rank.SUBGENUS));
    assertEquals(null, RankUtils.nextHigherLinneanRank(Rank.KINGDOM));
    assertEquals(null, RankUtils.nextHigherLinneanRank(Rank.DOMAIN));
    assertEquals(Rank.SPECIES, RankUtils.nextHigherLinneanRank(Rank.VARIETY));
  }

  @Test
  public void minRank() throws Exception {
    List<Rank> ranks = RankUtils.minRanks(Rank.GENUS);
    assertTrue(ranks.contains(Rank.GENUS));
    assertTrue(ranks.contains(Rank.FAMILY));
    assertTrue(ranks.contains(Rank.KINGDOM));
    assertTrue(ranks.contains(Rank.INFRACOHORT));
    assertFalse(ranks.contains(Rank.SUBGENUS));
    assertFalse(ranks.contains(Rank.SPECIES));
    assertEquals(54, ranks.size());
  }

  @Test
  public void maxRank() throws Exception {
    List<Rank> ranks = RankUtils.maxRanks(Rank.GENUS);
    assertTrue(ranks.contains(Rank.GENUS));
    assertFalse(ranks.contains(Rank.FAMILY));
    assertFalse(ranks.contains(Rank.KINGDOM));
    assertFalse(ranks.contains(Rank.INFRACOHORT));
    assertTrue(ranks.contains(Rank.SUBGENUS));
    assertTrue(ranks.contains(Rank.SPECIES));
    assertTrue(ranks.contains(Rank.SUBSPECIES));
    assertTrue(ranks.contains(Rank.NATIO));
    assertEquals(38, ranks.size());
  }

  @Test
  public void between() throws Exception {
    Set<Rank> ranks = RankUtils.between(Rank.GENUS, Rank.FAMILY, true);
    assertTrue(ranks.contains(Rank.GENUS));
    assertTrue(ranks.contains(Rank.FAMILY));
    assertTrue(ranks.contains(Rank.SUBFAMILY));
    assertFalse(ranks.contains(Rank.SUPERFAMILY));
    assertEquals(9, ranks.size());

    ranks = RankUtils.between(Rank.GENUS, Rank.FAMILY, false);
    assertFalse(ranks.contains(Rank.GENUS));
    assertFalse(ranks.contains(Rank.FAMILY));
    assertTrue(ranks.contains(Rank.SUBFAMILY));
    assertFalse(ranks.contains(Rank.SUPERFAMILY));
    assertEquals(7, ranks.size());
  }


}