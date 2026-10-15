package life.catalogue.api.vocab;

import java.util.Set;

import org.junit.Test;

import static life.catalogue.api.vocab.TaxGroup.*;
import static org.junit.Assert.*;

public class TaxGroupTest {

  @Test
  public void root() {
    assertEquals(Set.of(Eukaryotes), Lepidoptera.roots());
    assertEquals(Set.of(Eukaryotes), Arthropods.roots());
    assertEquals(Set.of(Eukaryotes), Chordates.roots());
    assertEquals(Set.of(Eukaryotes), Animals.roots());
    assertEquals(Set.of(Eukaryotes), Angiosperms.roots());
    assertEquals(Set.of(Eukaryotes), Algae.roots());
  }

  @Test
  public void contains() {
    assertTrue(Animals.contains(Chordates));
    assertTrue(Animals.contains(Arthropods));
    assertTrue(Arthropods.contains(Lepidoptera));

    assertFalse(Chordates.contains(Lepidoptera));
    assertFalse(Plants.contains(Animals));
  }

  @Test
  public void classification() {
    assertEquals(Set.of(Eukaryotes, Animals, Arthropods, Insects), Lepidoptera.classification());
    assertEquals(Set.of(Eukaryotes, Plants, Protists), Algae.classification());
  }

  @Test
  public void code() {
    for (var g : TaxGroup.values()) {
      if (!g.parents.isEmpty()) {
        assertFalse(g.codes.isEmpty());
      } else {
        assertFalse(g.codes.isEmpty());
      }
    }
  }

  @Test
  public void phylopics() {
    for (var g : TaxGroup.values()) {
      assertNotNull(g.getPhylopic());
      assertNotNull(g.getIcon());
      assertNotNull(g.getIconSVG());
    }
  }

  @Test
  public void disparate() {
    assertTrue(Angiosperms.isDisparateTo(OtherMolluscs));
    assertTrue(Angiosperms.isDisparateTo(Prokaryotes));
    assertTrue(Angiosperms.isDisparateTo(Animals));
    assertTrue(Angiosperms.isDisparateTo(Arachnids));
    assertTrue(Angiosperms.isDisparateTo(Gymnosperms));
    assertTrue(Coleoptera.isDisparateTo(Hemiptera));
    assertTrue(Animals.isDisparateTo(Plants));
    assertTrue(Basidiomycetes.isDisparateTo(Ascomycetes));
    assertTrue(Basidiomycetes.isDisparateTo(Lepidoptera));

    assertFalse(Angiosperms.isDisparateTo(Plants));
    assertFalse(Plants.isDisparateTo(Angiosperms));
    assertFalse(Coleoptera.isDisparateTo(Insects));
    assertFalse(Algae.isDisparateTo(Plants));
    assertFalse(Algae.isDisparateTo(Protists));
    assertFalse(Plants.isDisparateTo(null));
    assertFalse(Animals.isDisparateTo(Chordates));

  }

  /**
   * Overlaps hold between a declared group itself and its partner or any group below that partner,
   * never between two more specific groups.
   */
  @Test
  public void overlaps() {
    assertOverlap(true, Animals, Protists);
    assertOverlap(true, Animals, Algae);
    assertOverlap(true, Insects, Protists);
    assertOverlap(true, Plants, Fungi);
    assertOverlap(true, Plants, Ascomycetes);
    assertOverlap(true, Fungi, Protists);
    assertOverlap(true, Fungi, Algae);

    assertOverlap(false, Insects, Algae);
    assertOverlap(false, Angiosperms, Ascomycetes);
    assertOverlap(false, Animals, Plants);
    assertOverlap(false, Animals, Fungi);
    assertOverlap(false, Plants, Protists);
    assertOverlap(false, Bacteria, Protists);
    assertOverlap(false, Prokaryotes, Animals);
    assertOverlap(false, Viruses, Animals);
    // groups that are not disparate do not overlap, they simply agree
    assertOverlap(false, Algae, Plants);
    assertOverlap(false, Insects, Animals);
    assertOverlap(false, Animals, Animals);
    assertFalse(Animals.overlaps(null));

    assertEquals(Set.of("Animals", "Fungi"), Protists.getOverlaps());
    assertNull(Insects.getOverlaps());
  }

  static void assertOverlap(boolean expected, TaxGroup g1, TaxGroup g2) {
    assertEquals(g1 + " ~ " + g2, expected, g1.overlaps(g2));
    assertEquals(g2 + " ~ " + g1, expected, g2.overlaps(g1));
  }
}
