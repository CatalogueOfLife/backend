package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class CslNameTest {

  @Test
  public void testToString() {
    CslName n = new CslName("Marco", "Hofft", "van der");
    assertEquals("van der Hofft, Marco", n.toString());

    n.setNonDroppingParticle(null);
    assertEquals("Hofft, Marco", n.toString());

    n.setGiven(null);
    assertEquals("Hofft", n.toString());
  }
}