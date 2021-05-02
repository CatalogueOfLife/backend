package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ParserConfigTest {

  @Test
  public void updateID() {
    ParserConfig pc = new ParserConfig();
    assertNull(pc.getId());
    assertNull(pc.getScientificName());
    assertNull(pc.getAuthorship());

    pc.updateID("Abies alba", "Miller");
    assertEquals("Abies alba|Miller", pc.getId());
    assertEquals("Abies alba", pc.getScientificName());
    assertEquals("Miller", pc.getAuthorship());
  }
}