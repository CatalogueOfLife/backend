package life.catalogue.resources.matching.gbif;

import life.catalogue.api.vocab.MatchType;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The test cases of matching-ws' MatchingServiceStrictIT (https://github.com/gbif/matching-ws, Apache License 2.0):
 * strict matching never falls back to a higher rank. The kingdom is sent as matching-ws does, its Kingdom enum name.
 */
public class MatchingWsStrictTest extends MatchingWsTestBase {
  static final String PLANTAE = "PLANTAE";
  static final String ANIMALIA = "ANIMALIA";
  static final String INCERTAE_SEDIS = "INCERTAE_SEDIS";

  private static GbifNameUsageMatch query(String name, Rank rank, String kingdom) {
    var cl = cl();
    cl.setKingdom(kingdom);
    return query(null, name, rank, cl, true);
  }

  private static void assertMatch(String name, Rank rank, String kingdom, Integer expectedKey) {
    var best = query(name, rank, kingdom);
    print(name, best);
    check(name, () -> {
      assertEquals(String.valueOf(expectedKey), best.usage == null ? null : best.usage.key);
      assertNotEquals("Wrong match type", MatchType.NONE.name(), best.diagnostics.matchType);
      assertMatchConsistency(best);
    }, best);
  }

  private static void assertNoMatch(String name, Rank rank, String kingdom) {
    var best = query(name, rank, kingdom);
    print(name, best);
    check(name, () -> {
      assertEquals(MatchType.NONE.name(), best.diagnostics.matchType);
      assertNull(best.usage);
    }, best);
  }

  @Test
  public void testMatching() {
    assertMatch("Abies", Rank.GENUS, PLANTAE, 2684876);

    assertNoMatch("Abies alba", Rank.SPECIES, ANIMALIA);
    assertNoMatch("Abies alba", Rank.GENUS, PLANTAE);
    assertNoMatch("Abies alba", Rank.SUBSPECIES, PLANTAE);

    assertMatch("Abies alba", Rank.SPECIES, PLANTAE, 2685484);
    assertMatch("Abies alba", Rank.SPECIES, PLANTAE, 2685484);
    known(Known.YEAR_ONLY); assertMatch("Abies alba 1768", Rank.SPECIES, PLANTAE, 2685484);
    assertMatch("Abies alba Mill.", Rank.SPECIES, PLANTAE, 2685484);
    assertMatch("Abies alba Miller", Rank.SPECIES, PLANTAE, 2685484);
    assertMatch("Abies alba Mill., 1800", Rank.SPECIES, INCERTAE_SEDIS, 2685484);
    assertNoMatch("Abies alba DC", Rank.SPECIES, PLANTAE);
    assertNoMatch("Abies alba de Candolle, 1769", Rank.SPECIES, PLANTAE);
    assertNoMatch("Abies alba Linnaeus", Rank.SPECIES, PLANTAE);
    assertNoMatch("Abies alba L., 1989", Rank.SPECIES, PLANTAE);
    // matching-ws has this case commented out:
    // assertMatch("Abies alba nothing but a year, 1768", Rank.SPECIES, INCERTAE_SEDIS, 2685484);
  }

  /**
   * https://github.com/gbif/checklistbank/issues/7
   */
  @Test
  public void testIssue7() {
    assertMatch("Nicotiana langsdorfii J.A. Weinm.", Rank.SPECIES, PLANTAE, 2928759);

    // TODO (matching-ws) Eragrostis brownii (Kunth) Nees ex Wight and Eragrostis brownii (Kunth) Nees should match to the same key
    assertMatch("Eragrostis brownii (Kunth) Nees ex Wight", Rank.SPECIES, PLANTAE, 4149175);
    assertMatch("Eragrostis brownii (Kunth) Nees", Rank.SPECIES, PLANTAE, 4149175);

    assertNoMatch("Linaria bipartita (Vent.) Desf.", Rank.SPECIES, PLANTAE);
    assertMatch("Linaria bipartita (Vent.) Willd.", Rank.SPECIES, PLANTAE, 5415006);

    assertMatch("Modiola caroliniana (L.) G. Don f.", Rank.SPECIES, PLANTAE, 8338793);
    assertMatch("Modiola caroliniana (L.) G. Don fil.", Rank.SPECIES, PLANTAE, 8338793);

    assertMatch("Oenothera affinis Cambess. ex A. St. Hil.", Rank.SPECIES, PLANTAE, 3188847);
    assertMatch("Oenothera affinis Loud.", Rank.SPECIES, PLANTAE, 7860544);
    assertMatch("Oenothera affinis Camb.", Rank.SPECIES, PLANTAE, 3188847);

    assertMatch("Malva setigera F.K. Schimp. et Spenn.", Rank.SPECIES, PLANTAE, 3940638);
    assertMatch("Nicotiana langsdorfii J.A. Weinm.", Rank.SPECIES, PLANTAE, 2928759);
    assertMatch("Nonea lutea (Desr.) DC. ex Lam. et DC.", Rank.SPECIES, PLANTAE, 2926042);
    assertNoMatch("Veronica austriaca Jacq.", Rank.SPECIES, PLANTAE);
    assertNoMatch("Trisetaria panicea (Lam.) Maire", Rank.SPECIES, PLANTAE);
  }

  @Test
  public void testFilius() {
    assertMatch("Hydrocotyle ranunculoides L. f.", Rank.SPECIES, PLANTAE, 7978544);
    assertMatch("Hydrocotyle ranunculoides L.f.", Rank.SPECIES, PLANTAE, 7978544);
    assertMatch("Hydrocotyle ranunculoides Linnaeus filius", Rank.SPECIES, PLANTAE, 7666318);
  }
}
