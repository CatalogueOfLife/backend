package org.col.release;

import java.io.File;
import java.io.StringWriter;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.WsServerConfig;
import org.col.api.model.Dataset;
import org.col.api.vocab.Datasets;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.TestDataRule;
import org.junit.*;

public class AcExporterTest {
  
  WsServerConfig cfg;
  File arch;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.draftWithSectors();
  
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
    AcExporter exp = new AcExporter(cfg, PgSetupRule.getSqlSessionFactory());
    // prepare metadata
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(Datasets.DRAFT_COL);
      d.setCitation("Roskov Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life");
      dm.update(d);
    }
    
    StringWriter writer = new StringWriter();
    arch = exp.export(Datasets.DRAFT_COL, writer);
    System.out.println("LOGS:\n");
    System.out.println(writer.toString());
  }
  
}