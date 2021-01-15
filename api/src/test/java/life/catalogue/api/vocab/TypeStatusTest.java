package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class TypeStatusTest {

  @Test
  public void primary() {
    System.out.println("Primary:");
    for (TypeStatus t : TypeStatus.values()) {
      if (t.isPrimary()) {
        System.out.println(t);
      }
    }

    System.out.println("\nOther:");
    for (TypeStatus t : TypeStatus.values()) {
      if (!t.isPrimary()) {
        System.out.println(t);
      }
    }
    assertTrue(TypeStatus.HOLOTYPE.isPrimary());
    assertTrue(TypeStatus.LECTOTYPE.isPrimary());
    assertTrue(TypeStatus.SYNTYPE.isPrimary());
  }
}