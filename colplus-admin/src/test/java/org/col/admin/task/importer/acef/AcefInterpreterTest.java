package org.col.admin.task.importer.acef;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class AcefInterpreterTest {

  @Test
  public void latinName() throws Exception {
    assertEquals("Abies", AcefInterpreter.latinName("Abies"));
    assertEquals("Döring", AcefInterpreter.latinName("Döring"));
    assertEquals("Bào wén dōng fāng tún", AcefInterpreter.latinName("Bào wén dōng fāng tún"));
    assertEquals("bào wén duō jì tún", AcefInterpreter.latinName("豹紋多紀魨"));
  }

  @Test
  public void asciiName() throws Exception {
    assertEquals("Abies", AcefInterpreter.asciiName("Abiés"));
    assertEquals("Döring", AcefInterpreter.latinName("Döring"));
    assertEquals("Bao wen dong fang tun", AcefInterpreter.asciiName("Bào wén dōng fāng tún"));
    assertEquals("bao wen duo ji tun", AcefInterpreter.asciiName("豹紋多紀魨"));
  }
}