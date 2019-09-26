package org.col.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class SectorImportTest {
  
  @Test
  public void testEquals() {
    SectorImport si1 = new SectorImport();
    SectorImport si2 = new SectorImport();
    
    assertEquals(si1, si2);
    si1.setType("dfghjk");
    si2.setType("dfghjk");
    assertEquals(si1, si2);
    
    si1.addWarning("fghj");
    assertNotEquals(si1, si2);
  
    si2.addWarning("fghj2");
    // we only compare warning sizes, not content!!!
    assertEquals(si1, si2);
    
    si1.addWarning("fghj2");
    si2.addWarning("fghj");
    assertEquals(si1, si2);
  }
}