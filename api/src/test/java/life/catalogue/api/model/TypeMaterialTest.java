package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;

import org.junit.Test;

import static org.junit.Assert.*;

public class TypeMaterialTest {

  @Test
  public void buildCitation() {
    assertEquals("UGANDA • ♀; Imatong Mountains, near border with South Sudan; 3° 47' 24'', 32° 52' 11.999''; 2134m; 11 Aug. 1955; L.C. Beadle; genbank-seq-num; G-DC 1234", TypeMaterial.buildCitation(TestEntityGenerator.newType(1,"d")));
  }
}