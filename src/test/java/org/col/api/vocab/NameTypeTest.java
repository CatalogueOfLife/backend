package org.col.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameTypeTest {

  @Test
  public void testIsBackbone() throws Exception {
    assertTrue(NameType.SCIENTIFIC.isBackboneType());
    assertTrue(NameType.VIRUS.isBackboneType());
    assertTrue(NameType.DOUBTFUL.isBackboneType());

    assertFalse(NameType.PLACEHOLDER.isBackboneType());
    assertFalse(NameType.HYBRID.isBackboneType());
    assertFalse(NameType.INFORMAL.isBackboneType());
    assertFalse(NameType.CULTIVAR.isBackboneType());
    assertFalse(NameType.NO_NAME.isBackboneType());
    assertFalse(NameType.OTU.isBackboneType());
  }

  @Test
  public void testIsParsable() throws Exception {
    assertTrue(NameType.SCIENTIFIC.isParsable());
    assertTrue(NameType.INFORMAL.isParsable());
    assertTrue(NameType.DOUBTFUL.isParsable());

    assertFalse(NameType.VIRUS.isParsable());
    assertFalse(NameType.NO_NAME.isParsable());
    assertFalse(NameType.HYBRID.isParsable());
    assertFalse(NameType.OTU.isParsable());
  }

}