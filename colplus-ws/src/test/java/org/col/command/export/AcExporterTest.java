package org.col.command.export;

import java.io.File;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.api.vocab.Datasets;
import org.col.WsServerConfig;
import org.col.db.MybatisTestUtils;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.junit.*;

public class AcExporterTest {
  
  WsServerConfig cfg;
  File arch;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  @Before
  public void initCfg()  {
    cfg = new WsServerConfig();
    cfg.db = PgSetupRule.getCfg();
    cfg.downloadDir = Files.createTempDir();
    cfg.normalizer.scratchDir  = Files.createTempDir();
  }
  
  @After
  public void cleanup()  {
    FileUtils.deleteQuietly(cfg.downloadDir);
    FileUtils.deleteQuietly(cfg.normalizer.scratchDir);
    if (arch != null) {
      FileUtils.deleteQuietly(arch);
    }
  }
  
  @Test
  public void export() throws Exception {
    MybatisTestUtils.populateDraftTree(testDataRule.getSqlSession());
    AcExporter exp = new AcExporter(cfg);
    arch = exp.export(Datasets.DRAFT_COL);
  }
}