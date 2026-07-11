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
    // Fallback path: parse returns empty -> normalise whole label. Must not throw.
    var r = ChangedMatcher.match(
      List.of("?incertae?"),
      List.of("?incertae? sp"),
      1);
    assertNotNull(r);
    // Both labels survive the fallback path (parse empty -> normalise whole label); exact bucketing is
    // not asserted, only that the call completes and accounts for both inputs.
    assertEquals(2, r.changed().size() * 2 + r.removed().size() + r.added().size());
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
