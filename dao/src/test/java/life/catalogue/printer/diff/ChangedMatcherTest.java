package life.catalogue.printer.diff;

import life.catalogue.matching.similarity.ScientificNameSimilarity;
import life.catalogue.printer.NamesDiff;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChangedMatcherTest {

  private static final ScientificNameSimilarity SIM = new ScientificNameSimilarity();

  @Test
  public void pairsSimilarNames() {
    var r = ChangedMatcher.match(
      List.of("Abies alba", "Zea mays L."),
      List.of("Abies albus", "Quercus robur"),
      50.0, SIM);
    assertEquals(1, r.changed().size());
    assertEquals("Abies alba", r.changed().get(0).before());
    assertEquals("Abies albus", r.changed().get(0).after());
    assertEquals(List.of("Zea mays L."), r.removed());
    assertEquals(List.of("Quercus robur"), r.added());
  }

  @Test
  public void identicalPairsAreHealedNotChanged() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Aus aus"), 50.0, SIM);
    assertTrue(r.changed().isEmpty());
    assertTrue(r.removed().isEmpty());
    assertTrue(r.added().isEmpty());
  }

  @Test
  public void differentGenusNotPaired() {
    var r = ChangedMatcher.match(List.of("Aus aus"), List.of("Zea mays"), 50.0, SIM);
    assertTrue(r.changed().isEmpty());
    assertEquals(List.of("Aus aus"), r.removed());
    assertEquals(List.of("Zea mays"), r.added());
  }

  @Test
  public void assembleTruncates() {
    NamesDiff d = NamesDiffEngine.assemble("a", "b",
      new java.util.ArrayList<>(List.of("A a", "B b", "C c")),
      new java.util.ArrayList<>(List.of("X x", "Y y")),
      DiffOptions.defaults().setMaxItems(2));
    assertTrue(d.isTruncated());
    assertEquals(2, d.getRemoved().size());
    assertEquals(2, d.getAdded().size());
  }
}
