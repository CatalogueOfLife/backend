package life.catalogue.command;

import life.catalogue.junit.TestDataRule;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class MatchingServerBuildCmdIT extends CmdTestBase {

  public MatchingServerBuildCmdIT() {
    super(MatchingServerBuildCmd::new, TestDataRule.apple(), "config-matcher-test.yaml", false);
  }

  @Before
  public void init() {
  }

  @Test
  public void testBuild() throws Exception {
    assertTrue(run("matchingServerBuild", "--delete", "--key", Integer.toString(TestDataRule.APPLE.key)).isEmpty());
  }

}