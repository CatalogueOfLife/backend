package org.col.api.vocab;

import org.junit.Test;

/**
 *
 */
public class NomenclaturalStatusTest {

  @Test
  public void isAccepted() throws Exception {
    for (NomenclaturalStatus ns : NomenclaturalStatus.values()) {
      System.out.println(ns.name() + " -> " + (ns.isLegitimate() ? "accepted" : "not"));
    }
  }

}