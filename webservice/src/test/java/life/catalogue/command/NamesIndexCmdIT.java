package life.catalogue.command;

import life.catalogue.db.TestDataRule;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NamesIndexCmdIT extends CmdTestBase {

  public NamesIndexCmdIT() {
    super(NamesIndexCmd::new, TestDataRule.apple());
  }
  
  @Test
  public void testRebuild() throws Exception {
    assertTrue(run("nidx", "--prompt", "0").isEmpty());
  }

}