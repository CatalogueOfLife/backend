package life.catalogue.importer;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.importer.reference.ReferenceStore;
import life.catalogue.api.model.Dataset;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;

public class InterpreterBaseTest {
  
  @Mock
  ReferenceStore refStore;
  IssueContainer issues = new VerbatimRecord();
  InterpreterBase inter = new InterpreterBase(new Dataset(), new ReferenceFactory(1, refStore), null);
  
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
