package life.catalogue.api.vocab;

import org.junit.Test;

import java.util.Set;

import static life.catalogue.api.vocab.TaxGroup.*;
import static org.junit.Assert.*;

public class TaxGroupTest {

  @Test
  public void root() {
    assertEquals(Set.of(Eukaryotes), Birds.roots());
    assertEquals(Set.of(Eukaryotes), Arthropods.roots());
    assertEquals(Set.of(Eukaryotes), Chordates.roots());
    assertEquals(Set.of(Eukaryotes), Animals.roots());
    assertEquals(Set.of(Eukaryotes), Angiosperms.roots());
    assertEquals(Set.of(Eukaryotes), Algae.roots());
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
    assertEquals(Set.of(Eukaryotes, Animals, Chordates), Birds.classification());
    assertEquals(Set.of(Eukaryotes, Plants, Protists), Algae.classification());
  }

  @Test
  public void code() {
    for (var g : TaxGroup.values()) {
      if (!g.parents.isEmpty()) {
        assertFalse(g.codes.isEmpty());
      } else if (g != OtherEukaryotes) {
        assertFalse(g.codes.isEmpty());
      } else {
        assertTrue(g.codes.isEmpty());
      }
    }
  }

  @Test
  public void phylopics() {
    for (var g : TaxGroup.values()) {
      if (g.name().startsWith("Other")) {
        assertNull(g.getPhylopic());
        assertNull(g.getIcon());
        assertNull(g.getIconSVG());
      } else {
        assertNotNull(g.getPhylopic());
        assertNotNull(g.getIcon());
        assertNotNull(g.getIconSVG());
      }
    }
  }

  @Test
  public void disparate() {
    assertTrue(Angiosperms.isDisparateTo(OtherEukaryotes));
    assertTrue(Angiosperms.isDisparateTo(Prokaryotes));
    assertTrue(Angiosperms.isDisparateTo(Animals));
    assertTrue(Angiosperms.isDisparateTo(Arachnids));
    assertTrue(Angiosperms.isDisparateTo(Gymnosperms));
    assertTrue(Coleoptera.isDisparateTo(Hemiptera));
    assertTrue(Animals.isDisparateTo(Plants));
    assertTrue(Basidiomycetes.isDisparateTo(Ascomycetes));
    assertTrue(Basidiomycetes.isDisparateTo(Mammals));

    assertFalse(Angiosperms.isDisparateTo(Plants));
    assertFalse(Plants.isDisparateTo(Angiosperms));
    assertFalse(Coleoptera.isDisparateTo(Insects));
    assertFalse(Algae.isDisparateTo(Plants));
    assertFalse(Algae.isDisparateTo(Protists));
    assertFalse(Plants.isDisparateTo(null));
    assertFalse(Animals.isDisparateTo(Chordates));

  }
}