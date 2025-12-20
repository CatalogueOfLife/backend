package life.catalogue.command;

import life.catalogue.junit.TestDataRule;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class MatcherIndexCmdIT extends CmdTestBase {

  public MatcherIndexCmdIT() {
    super(MatcherIndexCmd::new, TestDataRule.apple(), "config-matcher-test.yaml", false);
  }

  @Before
  public void init() {
  }

  @Test
  public void testBuild() throws Exception {
    assertTrue(run("matcher", "--delete", "true", "--key", Integer.toString(TestDataRule.APPLE.key)).isEmpty());
  }

}