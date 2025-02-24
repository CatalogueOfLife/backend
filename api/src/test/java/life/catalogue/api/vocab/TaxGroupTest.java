package life.catalogue.api.vocab;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
}