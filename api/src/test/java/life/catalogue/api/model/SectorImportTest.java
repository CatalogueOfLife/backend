package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SectorImportTest {
  
  @Test
  public void testEquals() {
    SectorImport si1 = new SectorImport();
    SectorImport si2 = new SectorImport();
    
    assertEquals(si1, si2);
    si1.setJob("dfghjk");
    si2.setJob("dfghjk");
    assertEquals(si1, si2);
    
    si1.addWarning("fghj");
    assertNotEquals(si1, si2);
  
    si2.addWarning("fghj2");
    assertNotEquals(si1, si2);
    
    si1.addWarning("fghj2");
    si2.addWarning("fghj");
    // wrong order
    assertNotEquals(si1, si2);

    si1.getWarnings().clear();
    si2.getWarnings().clear();
    assertEquals(si1, si2);

    si1.addWarning("fghj");
    si1.addWarning("fghj2");
    si2.addWarning("fghj");
    si2.addWarning("fghj2");
    assertEquals(si1, si2);
  }
}