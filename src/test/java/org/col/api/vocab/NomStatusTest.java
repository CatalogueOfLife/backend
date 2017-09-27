package org.col.api.vocab;

import org.junit.Test;

/**
 *
 */
public class NomStatusTest {

  @Test
  public void isAccepted() throws Exception {
    for (NomStatus ns : NomStatus.values()) {
      System.out.println(ns.name() + " -> " + (ns.isLegitimate() ? "accepted" : "not"));
    }
  }

}