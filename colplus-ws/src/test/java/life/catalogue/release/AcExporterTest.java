package life.catalogue.release;

import java.io.File;

import com.google.common.io.Files;
import life.catalogue.release.AcExporter;
import life.catalogue.release.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.TestDataRule;
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
      System.out.println(arch.getAbsolutePath());
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
    
    Logger lg = new Logger();
    arch = exp.export(Datasets.DRAFT_COL, lg);
    System.out.println("LOGS:\n");
    System.out.println(lg.toString());
  }
  
}