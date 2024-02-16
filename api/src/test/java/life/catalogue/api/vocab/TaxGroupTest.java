package life.catalogue.api.vocab;

import org.junit.Test;

import java.util.Set;

import static life.catalogue.api.vocab.TaxGroup.*;
import static org.junit.Assert.*;

public class TaxGroupTest {

  @Test
  public void root() {
    assertEquals(Set.of(Animals), Birds.roots());
    assertEquals(Set.of(Animals), Arthropods.roots());
    assertEquals(Set.of(Animals), Chordates.roots());
    assertEquals(Set.of(Animals), Animals.roots());
    assertEquals(Set.of(Plants), Angiosperms.roots());
    assertEquals(Set.of(Plants, Protists), Algae.roots());
  }

  @Test
  public void contains() {
    assertTrue(Animals.contains(Birds));
    assertTrue(Animals.contains(Arthropods));
    assertTrue(Chordates.contains(Birds));

    assertFalse(Mammals.contains(Birds));
    assertFalse(Plants.contains(Animals));
  }

  @Test
  public void classification() {
    assertEquals(Set.of(Animals, Chordates), Birds.classification());
    assertEquals(Set.of(Plants, Protists), Algae.classification());
  }

  @Test
  public void code() {
    for (var g : TaxGroup.values()) {
      if (!g.parents.isEmpty()) {
        assertFalse(g.codes.isEmpty());
      } else if (g != Other) {
        assertFalse(g.codes.isEmpty());
      } else {
        assertTrue(g.codes.isEmpty());
      }
    }
  }

  @Test
  public void disparate() {
    assertTrue(Angiosperms.isDisparateTo(Other));
    assertTrue(Angiosperms.isDisparateTo(Prokaryotes));
    assertTrue(Angiosperms.isDisparateTo(Animals));
    assertTrue(Angiosperms.isDisparateTo(Arachnids));
    assertTrue(Angiosperms.isDisparateTo(Gymnosperms));
    assertTrue(Coleoptera.isDisparateTo(Hemiptera));

    assertFalse(Angiosperms.isDisparateTo(Plants));
    assertFalse(Plants.isDisparateTo(Angiosperms));
    assertFalse(Coleoptera.isDisparateTo(Insects));
    assertFalse(Algae.isDisparateTo(Plants));
    assertFalse(Algae.isDisparateTo(Protists));
    assertFalse(Plants.isDisparateTo(null));
  }
}