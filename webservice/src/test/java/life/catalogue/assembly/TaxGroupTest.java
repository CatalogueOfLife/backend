package life.catalogue.assembly;

import life.catalogue.api.vocab.TaxGroup;

import org.junit.Test;

import static org.junit.Assert.*;
import static life.catalogue.api.vocab.TaxGroup.*;

public class TaxGroupTest {

  @Test
  public void codfe() {
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