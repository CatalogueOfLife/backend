package life.catalogue.assembly;

import life.catalogue.api.vocab.TaxGroup;

import org.junit.Test;

import static org.junit.Assert.*;
import static life.catalogue.api.vocab.TaxGroup.*;

public class TaxGroupTest {

  @Test
  public void root() {
    assertEquals(Animals, Birds.root());
    assertEquals(Animals, Arthropods.root());
    assertEquals(Animals, Chordates.root());
    assertEquals(Animals, Animals.root());
    assertEquals(Plants, Angiosperms.root());
  }

  @Test
  public void contains() {
    assertTrue(Birds.contains(Animals));
    assertTrue(Arthropods.contains(Animals));
    assertTrue(Birds.contains(Chordates));

    assertFalse(Birds.contains(Mammals));
    assertFalse(Animals.contains(Plants));
  }

  @Test
  public void level() {
    for (var g : TaxGroup.values()) {
      if (g.getParent() != null) {
        assertTrue(g.level() > 1);
      } else {
        assertTrue(g.level() == 1);
      }
    }
  }

  @Test
  public void code() {
    for (var g : TaxGroup.values()) {
      if (g.getParent() != null) {
        assertNotNull(g.getCode());
        assertEquals(g.getParent().getCode(), g.getCode());
      } else if (g != Other && g != Protists) {
        assertNotNull(g.getCode());
      } else {
        assertNull(g.getCode());
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
    assertFalse(Plants.isDisparateTo(null));
  }
}