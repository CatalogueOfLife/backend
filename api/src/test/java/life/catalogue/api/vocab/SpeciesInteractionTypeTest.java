package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class SpeciesInteractionTypeTest {

  @Test
  public void inverse() {
    for (SpeciesInteractionType t : SpeciesInteractionType.values()) {
      System.out.println(t);
      assertTrue(SpeciesInteractionType.INVERSE.containsKey(t));
      if (t.isBidirectional()) {
        assertEquals(t, t.getInverse());
      } else {
        assertNotEquals(t, t.getInverse());
        assertEquals(t, t.getInverse().getInverse());
      }
    }
  }

}