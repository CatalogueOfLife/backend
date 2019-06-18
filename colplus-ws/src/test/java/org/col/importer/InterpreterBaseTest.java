package org.col.importer;

import org.col.api.model.IssueContainer;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.col.importer.reference.ReferenceFactory;
import org.col.importer.reference.ReferenceStore;
import org.col.api.model.Dataset;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;

public class InterpreterBaseTest {
  
  @Mock
  ReferenceStore refStore;
  IssueContainer issues = new VerbatimRecord();
  InterpreterBase inter = new InterpreterBase(new Dataset(), new ReferenceFactory(1, refStore), null);

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
  public void yearParser() throws Exception {
    assertEquals((Integer) 1678, InterpreterBase.parseYear("1678", issues));
    assertFalse(issues.hasIssues());
  
    assertEquals((Integer) 1678, InterpreterBase.parseYear("1678b", issues));
    assertFalse(issues.hasIssues());
  
    assertEquals((Integer) 1678, InterpreterBase.parseYear(" 1678 b", issues));
    assertFalse(issues.hasIssues());
    
    assertEquals((Integer) 999, InterpreterBase.parseYear("999", issues));
    assertTrue(issues.hasIssue(Issue.UNLIKELY_YEAR));
    
    issues.getIssues().clear();
    assertEquals((Integer) 2112, InterpreterBase.parseYear("2112", issues));
    assertTrue(issues.hasIssue(Issue.UNLIKELY_YEAR));
  
    issues.getIssues().clear();
    assertEquals((Integer) 2800, InterpreterBase.parseYear("2800", issues));
    assertTrue(issues.hasIssue(Issue.UNLIKELY_YEAR));

    issues.getIssues().clear();
    assertEquals((Integer) 1980, InterpreterBase.parseYear("198?", issues));
    assertFalse(issues.hasIssues());
  
    issues.getIssues().clear();
    assertNull(InterpreterBase.parseYear("gd2000", issues));
    assertTrue(issues.hasIssue(Issue.UNPARSABLE_YEAR));
  
    issues.getIssues().clear();
    assertNull(InterpreterBase.parseYear("35611", issues));
    assertTrue(issues.hasIssue(Issue.UNPARSABLE_YEAR));
  
    issues.getIssues().clear();
    assertNull(InterpreterBase.parseYear("january", issues));
    assertTrue(issues.hasIssue(Issue.UNPARSABLE_YEAR));
  }
  
}
