package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Issue;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.importer.reference.ReferenceStore;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.function.BiConsumer;

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

  @Test
  public void createDistributions() throws Exception {
    assertDistributions(Gazetteer.ISO, "DE", "DE");
    assertDistributions(Gazetteer.ISO, "DE,fr,es", "DE", "FR", "ES");
    assertDistributions(Gazetteer.ISO, "az-tar; AZ; ", "AZ-TAR", "AZ");
  }

  private void assertDistributions(Gazetteer std, String loc, String... expected) {
    List<Distribution> dis = InterpreterBase.createDistributions(std, loc, "present", new VerbatimRecord(), new BiConsumer<Distribution, VerbatimRecord>() {
      @Override
      public void accept(Distribution distribution, VerbatimRecord verbatimRecord) {
        // dont do anything
      }
    });

    int counter = 0;
    for (Distribution d : dis) {
      assertEquals(std, d.getGazetteer());
      assertEquals(expected[counter++], d.getArea());
    }
  }
}
