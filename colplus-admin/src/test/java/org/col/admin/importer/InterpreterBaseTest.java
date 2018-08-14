package org.col.admin.importer;

import org.col.admin.importer.reference.ReferenceFactory;
import org.col.admin.importer.reference.ReferenceStore;
import org.col.api.model.Dataset;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;

public class InterpreterBaseTest {

  @Mock
  ReferenceStore refStore;

  InterpreterBase inter = new InterpreterBase(new Dataset(), new ReferenceFactory(1, refStore));

  @Test
  public void latinName() throws Exception {
    assertEquals("Abies", inter.latinName("Abies"));
    assertEquals("Döring", inter.latinName("Döring"));
    assertEquals("Bào wén dōng fāng tún", inter.latinName("Bào wén dōng fāng tún"));
    assertEquals("bào wén duō jì tún", inter.latinName("豹紋多紀魨"));
  }

  @Test
  public void asciiName() throws Exception {
    assertEquals("Abies", inter.asciiName("Abiés"));
    assertEquals("Döring", inter.latinName("Döring"));
    assertEquals("Bao wen dong fang tun", inter.asciiName("Bào wén dōng fāng tún"));
    assertEquals("bao wen duo ji tun", inter.asciiName("豹紋多紀魨"));
  }

}
