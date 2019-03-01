package org.col.admin.command.export;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.AdminServerConfig;
import org.col.api.vocab.Datasets;
import org.col.db.MybatisTestUtils;
import org.col.db.PgSetupRule;
import org.col.db.mapper.InitMybatisRule;
import org.junit.*;

public class AcExporterTest {
  
  AdminServerConfig cfg;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.empty();
  
  @Before
  public void initCfg()  {
    cfg = new AdminServerConfig();
    cfg.db = pgSetupRule.getCfg();
    cfg.downloadDir = Files.createTempDir();
    cfg.scratchDir  = Files.createTempDir();
  }
  
  @After
  public void cleanup()  {
    FileUtils.deleteQuietly(cfg.downloadDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }
  
  @Test
  public void export() throws Exception {
    MybatisTestUtils.populateDraftTree(initMybatisRule.getSqlSession());
    AcExporter exp = new AcExporter(cfg);
    exp.export(Datasets.DRAFT_COL);
  }
}