package org.col.admin.command.initdb;

import org.col.admin.command.CmdTestBase;
import org.col.common.util.YamlUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class InitDbCmdTest extends CmdTestBase {

  public InitDbCmdTest() {
    super(new InitDbCmd());
  }

  @Test
  public void testInitCmd() throws Exception {
    assertTrue(run("initdb", "--prompt", "0"));
  }

  @Test
  public void testInitCmdFail() throws Exception {
    // use bad username & password -> expect error
    cfg.adminDb.user = "Fritz";
    cfg.adminDb.password = "Fröhlich";
    tempDbCfg.delete();
    YamlUtils.write(cfg, tempDbCfg);
    assertFalse(run("initdb", "--prompt", "0"));
  }
}