package life.catalogue.release;

import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.TestDataRule;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class ProjectDuplicationEmptyIT extends ProjectBaseIT {

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  @Test
  public void empty() throws Exception {
    ProjectDuplication dupl = projectCopyFactory.buildDuplication(Datasets.COL, Users.TESTER);
    dupl.run();
    assertEquals(ImportState.FINISHED, dupl.getMetrics().getState());
  }

}