package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChangedMatcherTest {

  @Test
  public void pairsAuthorshipAppended() {
    var r = ChangedMatcher.match(
      List.of("Abies alba", "Zea mays L."),
      List.of("Abies alba Mill.", "Quercus robur"),
      1);
    assertEquals(1, r.changed().size());
    assertEquals("Abies alba", r.changed().get(0).before());
    assertEquals("Abies alba Mill.", r.changed().get(0).after());
    assertEquals(List.of("Zea mays L."), r.removed());
    assertEquals(List.of("Quercus robur"), r.added());
  }

  @Test
  public void pairsShortAuthorshipAppended() {
    // The headline case: a short canonical whose whole-label Levenshtein was below the old 50% threshold.
    var r = ChangedMatcher.match(
      List.of("Accipiter nisus"),
      List.of("Accipiter nisus (Linnaeus, 1758)"),
      1);
    assertEquals(1, r.changed().size());
    assertTrue(r.removed().isEmpty());
    assertTrue(r.added().isEmpty());
  }

  @Test
  public void pairsAuthorshipReplacedOneInOneOut() {
    // Different authors, but only one candidate each -> reported as changed.
    var r = ChangedMatcher.match(
      List.of("Poa annua Mill."),
      List.of("Poa annua L."),
      1);
    assertEquals(1, r.changed().size());
    assertEquals("Poa annua Mill.", r.changed().get(0).before());
    assertEquals("Poa annua L.", r.changed().get(0).after());
  }

  @Test
  public void pairsCanonicalTypoWithinDistanceOne() {
    var r = ChangedMatcher.match(
      List.of("Accipiter nisus"),
      List.of("Accipiter nisius"),
      1);
    assertEquals(1, r.changed().size());
  }

  @Test
  public void differentSpeciesSameGenusNotPaired() {
    var r = ChangedMatcher.match(
      List.of("Accipiter nisus"),
      List.of("Accipiter minor"),
      1);
    assertTrue(r.changed().isEmpty());
    assertEquals(List.of("Accipiter nisus"), r.removed());
    assertEquals(List.of("Accipiter minor"), r.added());
  }

  @Test
  public void identicalPairsAreHealedNotChanged() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Aus aus"), 1);
    assertTrue(r.changed().isEmpty());
    assertTrue(r.removed().isEmpty());
    assertTrue(r.added().isEmpty());
  }

  @Test
  public void differentGenusNotPaired() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Zea mays"), 1);
    assertTrue(r.changed().isEmpty());
    assertEquals(List.of("Aus aus"), r.removed());
    assertEquals(List.of("Zea mays"), r.added());
  }

  @Test
  public void identicalHealedRegardlessOfPairing() {
    // A pairing must not consume an added string that a later removed item is identical to.
    var r = ChangedMatcher.match(
      List.of("Abies alphaz", "Abies alpha"),
      List.of("Abies alpha", "Abies alphax"),
      1);
    assertFalse(r.removed().contains("Abies alpha"));
    assertFalse(r.added().contains("Abies alpha"));
    assertTrue(r.changed().stream().noneMatch(c -> c.before().equals("Abies alpha") || c.after().equals("Abies alpha")));
    assertEquals(1, r.changed().size());
    assertEquals("Abies alphaz", r.changed().get(0).before());
    assertEquals("Abies alphax", r.changed().get(0).after());
  }

  @Test
  public void unparsableLabelsDoNotThrow() {
    // These labels are not actually unparsable: NameParser.PARSER.parse succeeds for both, returning a
    // PLACEHOLDER name whose scientificName is "? incertae" in each case (the trailing "sp" is dropped).
    // With identical normalised canonicals (distance 0 <= 1) and a single eligible candidate on each side,
    // they are paired unconditionally as one change rather than landing in removed/added. The real point of
    // this test is that odd, non-Linnean labels flow through the parser/normalizer without throwing.
    var r = ChangedMatcher.match(
      List.of("?incertae?"),
      List.of("?incertae? sp"),
      1);
    assertNotNull(r);
    assertEquals(1, r.changed().size());
    assertEquals("?incertae?", r.changed().get(0).before());
    assertEquals("?incertae? sp", r.changed().get(0).after());
    assertTrue(r.removed().isEmpty());
    assertTrue(r.added().isEmpty());
  }

  @Test
  public void multipleSynonymsWithDistinctYearsStayApart() {
    // Same canonical, distinct years -> AuthorComparator DIFFERENT -> none paired. The parenthetical forms
    // exercise the effective-authorship fallback: NameParser stores a parenthesised author/year in the
    // basionym slot, so parse() must read it there (not just combinationAuthorship) for the years to differ.
    var r = ChangedMatcher.match(
      List.of("Abax ellipticus (Cuvier, 1833)", "Abax ellipticus Schauberger, 1927"),
      List.of("Abax ellipticus Porta, 1901", "Abax ellipticus (Latreille, 1806)"),
      1);
    assertTrue(r.changed().isEmpty());
    assertEquals(2, r.removed().size());
    assertEquals(2, r.added().size());
  }

  @Test
  public void multipleSynonymsPairTheMatchingAuthorOnly() {
    // Two eligible added; only one has a compatible author -> that one pairs, the other stays added.
    var r = ChangedMatcher.match(
      List.of("Statice scoparia Pall. ex Willd."),
      List.of("Statice scoparia Pallas ex Willdenow", "Statice scoparia C.A.Mey. ex Boiss."),
      1);
    assertEquals(1, r.changed().size());
    assertEquals("Statice scoparia Pall. ex Willd.", r.changed().get(0).before());
    assertEquals("Statice scoparia Pallas ex Willdenow", r.changed().get(0).after());
    assertEquals(List.of("Statice scoparia C.A.Mey. ex Boiss."), r.added());
    assertTrue(r.removed().isEmpty());
  }

  @Test
  public void assembleTruncates() {
    NamesDiff d = NamesDiffEngine.assemble("a", "b",
      new java.util.ArrayList<>(List.of("Aus aa", "Bus bb", "Cus cc")),
      new java.util.ArrayList<>(List.of("Xus xx", "Yus yy")),
      DiffOptions.defaults().setMaxItems(2));
    assertTrue(d.isTruncated());
    assertEquals(2, d.getRemoved().size());
    assertEquals(2, d.getAdded().size());
  }
}
