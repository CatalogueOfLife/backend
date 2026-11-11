package life.catalogue.jobs;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.ArchivedNameUsageMapper.ArchivedSimpleName;
import life.catalogue.jobs.RedirectPairing.Outcome;
import life.catalogue.matching.NameIdentity;
import life.catalogue.matching.TaxGroupAnalyzer;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import static org.junit.Assert.*;

public class RedirectPairingTest {
  // 20 is the newest base release, 21 its extended release, 10 and 11 older releases
  static final int BASE = 20;
  static final int XR = 21;
  final RedirectPairing pairing = new RedirectPairing(new TaxGroupAnalyzer(),
    new IntOpenHashSet(new int[]{BASE, XR}), new IntOpenHashSet(new int[]{BASE}));

  static ArchivedSimpleName sn(String id, String name, String authorship, Rank rank, int... releaseKeys) {
    var sn = new ArchivedSimpleName();
    sn.setId(id);
    sn.setName(name);
    sn.setAuthorship(authorship);
    sn.setRank(rank);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    sn.setCode(NomCode.BOTANICAL);
    sn.setNamesIndexId(1);
    sn.setReleaseKeys(releaseKeys);
    return sn;
  }

  static ArchivedSimpleName species(String id, String authorship, int... releaseKeys) {
    return sn(id, "Abies alba", authorship, Rank.SPECIES, releaseKeys);
  }

  RedirectPairing.Pair single(ArchivedSimpleName... group) {
    var pairs = pairing.pair(List.of(group));
    assertEquals(1, pairs.size());
    return pairs.get(0);
  }

  static void assertRedirect(RedirectPairing.Pair p, String deadId, String liveId) {
    assertEquals(Outcome.REDIRECT, p.outcome());
    assertTrue(p.isRedirect());
    assertEquals(deadId, p.dead().getId());
    assertEquals(liveId, p.live().getId());
  }

  /**
   * data#1867: the COL21 id of Helosciadium lost to the id of an extended release duplicate in COL26.7
   */
  @Test
  public void helosciadium() {
    var p = single(
      sn("637G6", "Helosciadium", "W.D.J.Koch", Rank.GENUS, 10, 11),
      sn("VJTPP", "Helosciadium", "W. D. J. Koch", Rank.GENUS, 11, BASE, XR)
    );
    assertRedirect(p, "637G6", "VJTPP");
    assertEquals(NameIdentity.Evidence.CONFIRMED, p.verdict().evidence);
  }

  @Test
  public void authorCitedDifferently() {
    assertRedirect(single(species("BB", "Miller", 10), species("CC", "Mill.", BASE)), "BB", "CC");
  }

  @Test
  public void authorshipAddedIsPlausible() {
    var p = single(species("BB", null, 10), species("CC", "Mill.", BASE));
    assertRedirect(p, "BB", "CC");
    assertEquals(NameIdentity.Evidence.PLAUSIBLE, p.verdict().evidence);
  }

  @Test
  public void differentAuthorIsContradicted() {
    var p = single(species("BB", "DC.", 10), species("CC", "Mill.", BASE));
    assertEquals(Outcome.CONTRADICTED, p.outcome());
    // the closest live id and why it is not the same name
    assertEquals("CC", p.live().getId());
    assertEquals(NameIdentity.Conflict.AUTHORSHIP, p.verdict().conflict);
    assertEquals("authorship different author", p.verdict().explain());
  }

  /**
   * Dry run of the redirect backfill on prod, 2026-11-10: a catalogue citing a later user of a name as its author
   * gave 23,738 redirects onto the original name cited with its author in brackets. Some of them are misidentifications
   * of another species, like this synonym of a different Abraxas.
   */
  @Test
  public void differentAuthorAcrossBracketsIsContradicted() {
    var p = single(species("92QX8", "Matsumura, 1925", 10), species("8LSG", "(Linnaeus, 1758)", BASE));
    assertEquals(Outcome.CONTRADICTED, p.outcome());
    assertEquals("8LSG", p.live().getId());
    assertEquals("authorship different author across brackets", p.verdict().explain());
  }

  @Test
  public void sameAuthorAcrossBracketsRedirects() {
    var p = single(species("BB", "Pascoe, 1864", 10), species("CC", "(Pascoe, 1859)", BASE));
    assertRedirect(p, "BB", "CC");
    assertEquals(NameIdentity.Evidence.CONFIRMED, p.verdict().evidence);
    assertEquals("authorship brackets", p.verdict().explain());
  }

  @Test
  public void misappliedNameIsADifferentName() {
    var dead = species("BB", "Mill.", 10);
    dead.setStatus(TaxonomicStatus.MISAPPLIED);
    assertEquals(Outcome.CONTRADICTED, single(dead, species("CC", "Mill.", BASE)).outcome());
  }

  /**
   * Dry run of the redirect backfill on prod, 2026-11-10: a synonym archived from COL21 with its misapplication only in
   * the name phrase redirected to the name of Jaubert & Spach.
   */
  @Test
  public void misappliedByItsNoteIsADifferentName() {
    var dead = sn("5YSRT", "Cicer songaricum", null, Rank.SPECIES, 10);
    dead.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
    dead.setPhrase("auct. non (DC.) Bunge");
    var p = single(dead, sn("9CQP9", "Cicer songaricum", "Jaub. & Spach", Rank.SPECIES, BASE));
    assertEquals(Outcome.CONTRADICTED, p.outcome());
    assertEquals("misapplied", p.verdict().explain());
  }

  static ArchivedSimpleName misapplied(String id, String name, String authorship, String phrase, int... releaseKeys) {
    var sn = sn(id, name, authorship, Rank.SPECIES, releaseKeys);
    sn.setStatus(TaxonomicStatus.MISAPPLIED);
    sn.setCode(NomCode.ZOOLOGICAL);
    sn.setPhrase(phrase);
    return sn;
  }

  /**
   * Redirect backfill on prod, 2026-11-10: misapplied names archived from older releases were contradicted on their
   * phrase by the same misapplication, its author cited differently.
   */
  @Test
  public void sameMisapplicationCitedDifferentlyRedirects() {
    // the dead synonym of COL21 reads as misapplied by its phrase
    var dead = misapplied("9JRNK", "Anatina tasmanica", "Reeve, 1863", "sensu Hutton, 1873", 10);
    dead.setStatus(TaxonomicStatus.SYNONYM);
    assertRedirect(single(dead, misapplied("V966D", "Anatina tasmanica", null, "sensu F. W. Hutton, 1873", BASE)),
      "9JRNK", "V966D");

    assertRedirect(single(
      misapplied("4MGHJ", "Prionovolva brevis", null, "sensu Allan, 1956", 10),
      misapplied("V95WW", "Prionovolva brevis", "(G. B. Sowerby I, 1828)", "sensu J. K. Allan, 1956", BASE)
    ), "4MGHJ", "V95WW");

    // another author's sense is another misapplication
    var p = single(
      misapplied("BFSB3", "Chrotella cavernosa", null, "sensu Burton, 1959", 10),
      misapplied("V94RB", "Chrotella cavernosa", null, "sensu Tsurnamal, 1969", BASE)
    );
    assertEquals(Outcome.CONTRADICTED, p.outcome());
    assertEquals("phrase", p.verdict().explain());
  }

  @Test
  public void nothingCorroboratesIsWeak() {
    // an unranked name tells nothing about the rank, and neither side has an authorship
    var p = single(
      sn("BB", "Abies", null, Rank.UNRANKED, 10),
      sn("CC", "Abies", null, Rank.GENUS, BASE)
    );
    assertEquals(Outcome.WEAK, p.outcome());
    assertFalse(p.isRedirect());
    assertEquals("CC", p.live().getId());
  }

  @Test
  public void tieGoesToTheIdABaseReleaseCarries() {
    assertRedirect(single(
      species("BB", "Mill.", 10),
      species("CC", "Mill.", XR),
      species("DD", "Mill.", BASE, XR)
    ), "BB", "DD");
  }

  @Test
  public void remainingTieIsAmbiguous() {
    var p = single(
      species("BB", "Mill.", 10),
      species("CC", "Mill.", XR),
      species("DD", "Mill.", XR)
    );
    assertEquals(Outcome.AMBIGUOUS, p.outcome());
    assertFalse(p.isRedirect());
  }

  @Test
  public void betterEvidenceWinsOverBaseRelease() {
    // CC agrees on the author, DD only on the rank
    assertRedirect(single(
      species("BB", "Mill.", 10),
      species("CC", "Miller", XR),
      species("DD", null, BASE)
    ), "BB", "CC");
  }

  @Test
  public void equalAuthorshipOverrulesDisparateGroups() {
    var dead = sn("BB", "Oenanthe", "L.", Rank.GENUS, 10);
    dead.setClassification(List.of(new SimpleName(null, "Animalia", Rank.KINGDOM)));
    dead.setCode(NomCode.ZOOLOGICAL);
    var live = sn("CC", "Oenanthe", "L.", Rank.GENUS, BASE);
    live.setClassification(List.of(new SimpleName(null, "Plantae", Rank.KINGDOM)));
    var p = single(dead, live);
    assertRedirect(p, "BB", "CC");
    assertEquals(NameIdentity.Evidence.PLAUSIBLE, p.verdict().evidence);
  }

  static ArchivedSimpleName otu(String id, String name, int... releaseKeys) {
    var sn = sn(id, name, null, Rank.UNRANKED, releaseKeys);
    sn.setCode(null);
    return sn;
  }

  static void assertOtu(RedirectPairing.Pair p, String deadId, String liveId) {
    assertEquals(Outcome.OTU, p.outcome());
    assertTrue(p.isRedirect());
    assertEquals(deadId, p.dead().getId());
    assertEquals(liveId, p.live().getId());
    assertNull(p.verdict());
  }

  /**
   * OTUs had minted LATIN29 ids up to COL26.5 XR and use their code since
   */
  @Test
  public void boldBinRedirectsToItsCode() {
    assertOtu(single(
      otu("FN95X", "BOLD:AAM9176", 10),
      otu("BOLD.AAM9176", "BOLD:AAM9176", BASE, XR)
    ), "FN95X", "BOLD.AAM9176");
    // the code is case insensitive, the id upper case
    assertOtu(single(
      otu("FN95X", "bold:aam9176", 10),
      otu("BOLD.AAM9176", "BOLD:AAM9176", XR)
    ), "FN95X", "BOLD.AAM9176");
  }

  @Test
  public void uniteShRedirectsToItsCode() {
    assertOtu(single(
      otu("GK2VQ", "SH0210222.10FU", 10),
      otu("SH0210222.10FU", "SH0210222.10FU", BASE)
    ), "GK2VQ", "SH0210222.10FU");
  }

  @Test
  public void otuRedirectsOnlyToItsOwnCode() {
    // another version of the species hypothesis is another id
    var p = single(
      otu("GK2VQ", "SH0210222.09FU", 10),
      otu("SH0210222.10FU", "SH0210222.10FU", BASE)
    );
    assertEquals(Outcome.NO_TARGET, p.outcome());
    assertNull(p.live());
    // and an OTU never redirects to a LATIN29 id by its name
    p = single(
      otu("FN95X", "BOLD:AAM9176", 10),
      otu("CC", "BOLD:AAM9176", BASE)
    );
    assertEquals(Outcome.NO_TARGET, p.outcome());
    // nor is a live OTU id a target of a name based redirect
    p = single(
      species("BB", "Mill.", 10),
      species("BOLD.AAM9176", "Mill.", BASE)
    );
    assertEquals(Outcome.NO_TARGET, p.outcome());
  }

  @Test
  public void severalDeadIdsOfOneOtu() {
    var pairs = pairing.pair(List.of(
      otu("FN95X", "BOLD:AAM9176", 10),
      otu("FN96X", "BOLD:AAM9176", 11),
      otu("BOLD.AAM9176", "BOLD:AAM9176", BASE)
    ));
    assertEquals(2, pairs.size());
    assertOtu(pairs.get(0), "FN95X", "BOLD.AAM9176");
    assertOtu(pairs.get(1), "FN96X", "BOLD.AAM9176");
  }

  @Test
  public void noLiveId() {
    var p = single(species("BB", "Mill.", 10), species("A", "Mill.", BASE));
    // A is no LATIN29 id, so it is no redirect target
    assertEquals(Outcome.NO_TARGET, p.outcome());
    assertNull(p.live());
  }

  @Test
  public void manyDeadIdsOneLiveId() {
    var pairs = pairing.pair(List.of(
      species("BB", "Mill.", 10),
      species("CC", "Miller", 11),
      species("DD", "Mill.", BASE),
      species("E", "Mill.", 11) // a temporary id is never redirected
    ));
    assertEquals(2, pairs.size());
    assertRedirect(pairs.get(0), "BB", "DD");
    assertRedirect(pairs.get(1), "CC", "DD");
  }
}
