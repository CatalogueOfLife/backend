package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NomStatusTest {
  
  @Test
  public void isAccepted() throws Exception {
    for (NomStatus ns : NomStatus.values()) {
      System.out.println(ns.name() + " -> "
          + (ns.isAvailable() ? "available" : "inval")
          + " / "
          + (ns.isLegitimate() ? "legitimate" : "illeg")
      );
    }
  }

  @Test
  public void isCompatible() throws Exception {
    for (NomStatus ns : NomStatus.values()) {
      for (NomStatus ns2 : NomStatus.values()) {
        System.out.println(ns.name() + " -> " + ns2.name() + ": " + ns.isCompatible(ns2));
      }
    }
  }

  @Test
  public void mostDetailed() throws Exception {
    assertEquals(NomStatus.ACCEPTABLE, NomStatus.ESTABLISHED.mostDetailed(NomStatus.ACCEPTABLE));
    assertEquals(NomStatus.UNACCEPTABLE, NomStatus.ESTABLISHED.mostDetailed(NomStatus.UNACCEPTABLE));
    assertEquals(NomStatus.UNACCEPTABLE, NomStatus.UNACCEPTABLE.mostDetailed(NomStatus.ESTABLISHED));
    // non compatible ones always return the first argument
    assertEquals(NomStatus.ESTABLISHED, NomStatus.ESTABLISHED.mostDetailed(NomStatus.NOT_ESTABLISHED));
    assertEquals(NomStatus.NOT_ESTABLISHED, NomStatus.NOT_ESTABLISHED.mostDetailed(NomStatus.UNACCEPTABLE));
  }

}