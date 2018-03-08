package org.col.admin.task.importer;

import org.col.api.model.Name;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.admin.task.importer.InterpreterBase.isInconsistent;
import static org.junit.Assert.*;
/**
 *
 */
public class InterpreterBaseTest {
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterBaseTest.class);
  InterpreterBase inter = new InterpreterBase(null);
  
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
  
  @Test
  public void testIsInconsistent() throws Exception {
    LOG.warn("warn warn warn");
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);

    assertFalse(isInconsistent("1", n));

    n.setUninomial("Asteraceae");
    n.setRank(Rank.FAMILY);
    assertFalse(isInconsistent("1", n));
    for (Rank r : Rank.values()) {
      if (r.isSuprageneric()) {
        n.setRank(r);
        assertFalse(isInconsistent("1", n));
      }
    }

    n.setRank(Rank.GENUS);
    assertFalse(isInconsistent("1", n));

    n.setUninomial("Abies");
    assertFalse(isInconsistent("1", n));

    n.getCombinationAuthorship().getAuthors().add("Mill.");
    assertFalse(isInconsistent("1", n));

    n.setRank(Rank.SPECIES);
    assertTrue(isInconsistent("1", n));

    n.setInfragenericEpithet("Pinoideae");
    assertTrue(isInconsistent("1", n));

    n.setRank(Rank.SUBGENUS);
    // uninomial is not allowed!
    assertTrue(isInconsistent("1", n));

    n.setUninomial(null);
    n.setGenus("Abies");
    assertFalse(isInconsistent("1", n));

    n.setSpecificEpithet("alba");
    assertTrue(isInconsistent("1", n));

    n.setRank(Rank.SPECIES);
    assertFalse(isInconsistent("1", n));

    n.setInfragenericEpithet(null);
    assertFalse(isInconsistent("1", n));

    n.setRank(Rank.VARIETY);
    assertTrue(isInconsistent("1", n));

    n.setInfraspecificEpithet("alpina");
    assertFalse(isInconsistent("1", n));

    n.setRank(Rank.SPECIES);
    assertTrue(isInconsistent("1", n));

    n.setRank(Rank.UNRANKED);
    assertFalse(isInconsistent("1", n));

    n.setSpecificEpithet(null);
    assertTrue(isInconsistent("1", n));
  }

  /**
   * logger_name:org.col.admin.task.importer.acef.AcefInterpreter
   * message:Inconsistent name W-Msc-1005056: null/W-Msc-1005056[SCIENTIFIC] G:Marmorana IG:Ambigua S:saxetana R:SUBSPECIES IS:forsythi A:null BA:null
   */
  @Test
  public void isInconsistent2() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);

    n.setGenus("Marmorana");
    n.setInfragenericEpithet("Ambigua");
    n.setSpecificEpithet("saxetana");
    n.setInfraspecificEpithet("forsythi");
    n.setRank(Rank.SUBSPECIES);

    assertFalse(isInconsistent("1", n));
  }


}