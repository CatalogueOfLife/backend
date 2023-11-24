package life.catalogue.matching;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.ScientificName;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.gbif.nameparser.util.RankUtils;

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
  public void isMultiWord() throws Exception {
    assertFalse(NameValidator.isMultiWord("Abies"));
    assertFalse(NameValidator.isMultiWord("alba"));

    assertTrue(NameValidator.isMultiWord("a alba"));
    assertTrue(NameValidator.isMultiWord("Abies alba"));
    assertTrue(NameValidator.isMultiWord("× Abies")); // the hybrid marker should be removed in a parsed name!
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
      if (r.isSuprageneric() && r != Rank.FAMILY && !r.isUncomparable()) {
        n.setRank(r);
        verify(n, Issue.RANK_NAME_SUFFIX_CONFLICT);
      }
    }
    
    n.setRank(Rank.GENUS);
    verify(n, Issue.RANK_NAME_SUFFIX_CONFLICT);
    
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
   * logger_name:life.catalogue.admin.task.importer.acef.AcefInterpreter
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
  public void infragenerics() throws Exception {
    // infrageneric names should be given with an infragenericEpithet property, not uninomial
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    n.setRank(Rank.SUBGENUS);
    n.setUninomial("Abies");
    verify(n, Issue.INCONSISTENT_NAME);

    n.setGenus("Marmorana");
    verify(n, Issue.INCONSISTENT_NAME);

    n.setInfragenericEpithet("Abies");
    n.setUninomial(null);
    verify(n);

    n.setGenus(null);
    verify(n);

    n.setSpecificEpithet("saxetana");
    verify(n, Issue.INCONSISTENT_NAME);
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

  @Test
  public void epithets() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);

    n.setUninomial("Marmorana");
    verify(n);

    n.setUninomial("Marmorana magna");
    verify(n, Issue.MULTI_WORD_MONOMIAL);

    n.setUninomial("MARMORANA");
    verify(n, Issue.WRONG_MONOMIAL_CASE);

    n.setUninomial("marmorana");
    verify(n, Issue.WRONG_MONOMIAL_CASE);

    n.setUninomial("mArmorana");
    verify(n, Issue.WRONG_MONOMIAL_CASE);
  }

  @Test
  public void suffixBad() throws Exception {
    var nb = Name.newBuilder().type(NameType.SCIENTIFIC);

    // all no issues cause name is unranked
    verify(nb.uninomial("Chamberlinini").build());
    nb.code(NomCode.BOTANICAL);
    verify(nb.uninomial("Chamberlinini").build());
    nb.code(NomCode.ZOOLOGICAL);
    verify(nb.uninomial("Chamberlinini").build());

    nb.rank(Rank.GENUS);
    verify(nb.uninomial("Chamberlinini").build(), Issue.RANK_NAME_SUFFIX_CONFLICT);
    // botanical ones dont have such an ending
    nb.code(NomCode.BOTANICAL);
    verify(nb.uninomial("Chamberlinini").build());

    nb.code(NomCode.ZOOLOGICAL);
    nb.rank(Rank.TRIBE);
    verify(nb.uninomial("Chamberlinini").build());

    nb.code(NomCode.BOTANICAL);
    verify(nb.uninomial("Asteraceae").build(), Issue.RANK_NAME_SUFFIX_CONFLICT);
    nb.rank(Rank.FAMILY);
    verify(nb.uninomial("Asteraceae").build());
  }
}