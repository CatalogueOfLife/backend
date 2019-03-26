package org.col.importer;

import org.col.api.model.Name;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class NameValidatorTest {

  private void verify(Name n, Issue ... expected) {
    VerbatimRecord v = new VerbatimRecord();
    if (expected.length == 0) {
      assertNull( NameValidator.flagIssues(n, v) );

    } else {
      assertNotNull( NameValidator.flagIssues(n, v) );
      for (Issue iss : expected) {
        assertTrue(v.hasIssue(iss));
      }
    }
  }
  
  @Test
  public void nonLetterPattern() throws Exception {
    assertFalse(NameValidator.NON_LETTER.matcher("").find());
    assertFalse(NameValidator.NON_LETTER.matcher("Asteraceae").find());
    assertFalse(NameValidator.NON_LETTER.matcher("Cephaëlis").find());
    
    assertTrue(NameValidator.NON_LETTER.matcher("sax€tana").find());
    assertTrue(NameValidator.NON_LETTER.matcher("saxétana").find());
  }
  
  @Test
  public void unmatchedBrackets() throws Exception {
    assertFalse(NameValidator.hasUnmatchedBrackets(null));
    assertFalse(NameValidator.hasUnmatchedBrackets(""));
    assertFalse(NameValidator.hasUnmatchedBrackets("a"));
    assertFalse(NameValidator.hasUnmatchedBrackets(" "));
    assertFalse(NameValidator.hasUnmatchedBrackets("Ay (Du)"));
    
    assertTrue(NameValidator.hasUnmatchedBrackets("Ay (Du"));
    assertTrue(NameValidator.hasUnmatchedBrackets("Ay [ick?] (Du la"));
    assertFalse(NameValidator.hasUnmatchedBrackets("Ay [ick?] (Du) la"));
  }
  
  
  @Test
  public void testIsInconsistent() throws Exception {
    Name n = new Name();
    n.setId("#1");
    n.setType(NameType.SCIENTIFIC);
    
    verify(n);
    
    n.setUninomial("Asteraceae");
    n.setRank(Rank.FAMILY);
    verify(n);
    
    for (Rank r : Rank.values()) {
      if (r.isSuprageneric()) {
        n.setRank(r);
        verify(n);
      }
    }
    
    n.setRank(Rank.GENUS);
    verify(n);
    
    n.setUninomial("Abies");
    verify(n);
    
    n.getCombinationAuthorship().getAuthors().add("Mill.");
    verify(n);
    
    n.setRank(Rank.SPECIES);
    verify(n, Issue.INCONSISTENT_NAME);
    
    n.setInfragenericEpithet("Pinoideae");
    verify(n, Issue.INCONSISTENT_NAME);
    
    n.setRank(Rank.SUBGENUS);
    // uninomial is not allowed!
    verify(n, Issue.INCONSISTENT_NAME);
    
    n.setUninomial(null);
    n.setGenus("Abies");
    verify(n);
    
    n.setSpecificEpithet("alba");
    verify(n, Issue.INCONSISTENT_NAME);
    
    n.setRank(Rank.SPECIES);
    verify(n);
    
    n.setInfragenericEpithet(null);
    verify(n);
    
    n.setRank(Rank.VARIETY);
    verify(n, Issue.INCONSISTENT_NAME);
    
    n.setInfraspecificEpithet("alpina");
    verify(n);
    
    n.setRank(Rank.SPECIES);
    verify(n, Issue.INCONSISTENT_NAME);
    
    n.setRank(Rank.UNRANKED);
    verify(n);
    
    n.setSpecificEpithet(null);
    verify(n, Issue.INCONSISTENT_NAME);
  }
  
  /**
   * logger_name:org.col.admin.task.importer.acef.AcefInterpreter
   * message:Inconsistent name W-Msc-1005056: null/W-Msc-1005056[SCIENTIFIC] G:Marmorana IG:Ambigua S:saxetana R:SUBSPECIES IS:forsythi A:null BA:null
   */
  @Test
  public void isConsistent2() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    
    n.setGenus("Marmorana");
    n.setInfragenericEpithet("Ambigua");
    n.setSpecificEpithet("saxetana");
    n.setInfraspecificEpithet("forsythi");
    n.setRank(Rank.SUBSPECIES);
    
    verify(n);
  }
  
  @Test
  public void isConsistentChars() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    
    n.setGenus("Marmorana");
    verify(n);
    
    n.setSpecificEpithet("sax€tana");
    verify(n, Issue.UNUSUAL_NAME_CHARACTERS);
    
    n.setSpecificEpithet("saxetana");
    verify(n);
    
    n.setInfraspecificEpithet("forsythi");
    n.setRank(Rank.SUBSPECIES);
    verify(n);
    
    n.setInfraspecificEpithet("for sythi");
    verify(n, Issue.UNUSUAL_NAME_CHARACTERS);
  }
}