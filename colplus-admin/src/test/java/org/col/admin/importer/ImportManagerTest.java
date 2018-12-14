package org.col.admin.importer;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.matching.NameIndexFactory;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetOrigin;
import org.col.api.vocab.DatasetType;
import org.col.api.vocab.Users;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.InitMybatisRule;
import org.col.img.ImageService;
import org.junit.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

@Ignore
public class ImportManagerTest {
  
  ImportManager importManager;
  CloseableHttpClient hc;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.empty();
  
  private static AdminServerConfig provideConfig() {
    AdminServerConfig cfg = new AdminServerConfig();
    cfg.gbif.syncFrequency = 0;
    cfg.importer.continousImportPolling = 0;
    cfg.importer.threads = 2;
    cfg.normalizer.archiveDir = Files.createTempDir();
    cfg.normalizer.scratchDir = Files.createTempDir();
    cfg.db.host = "localhost";
    cfg.db.database = "colplus";
    cfg.db.user = "postgres";
    cfg.db.password = "postgres";
    cfg.es = null;
    return cfg;
  }
  
  @Before
  public void init() throws Exception {
    MetricRegistry metrics = new MetricRegistry();
    
    final AdminServerConfig cfg = provideConfig();
    
    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    importManager = new ImportManager(cfg, metrics, hc, PgSetupRule.getSqlSessionFactory(),
        NameIndexFactory.passThru(), null, new ImageService(cfg.img));
    importManager.start();
  }
  
  @After
  public void shutdown() throws Exception {
    importManager.stop();
    hc.close();
  }
  
  @Test
  public void cancel() {
  }
  
  private Dataset createExternal() {
    Dataset d = new Dataset();
    d.setType(DatasetType.GLOBAL);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Test dataset");
    d.setDataFormat(DataFormat.COLDP);
    d.getDataAccess();
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(false) ) {
      session.getMapper(DatasetMapper.class).create(d);
      session.commit();
    }
    return d;
  }
  @Test
  public void submit() throws Exception {
    final Dataset d1 = createExternal();
    final Dataset d2 = createExternal();
    final Dataset d3 = createExternal();
    final Dataset d4 = createExternal();
  
    importManager.submit(new ImportRequest(d1.getKey(), Users.IMPORTER));
    Thread.sleep(100);
    assertTrue(importManager.hasEmptyQueue());
    assertTrue(importManager.hasRunning());

    importManager.submit(new ImportRequest(d2.getKey(), Users.IMPORTER));
    importManager.submit(new ImportRequest(d3.getKey(), Users.IMPORTER));
    importManager.submit(new ImportRequest(d4.getKey(), Users.IMPORTER));
  
    assertFalse(importManager.hasEmptyQueue());
    assertEquals(2, importManager.list().size());
  }
  

  
  /**
   * Try with 3 small parallel datasets
   */
  @Test
  public void debugParallel() throws Exception {
    importManager.submit(new ImportRequest(1000, Users.IMPORTER));
    importManager.submit(new ImportRequest(1006, Users.IMPORTER));
    importManager.submit(new ImportRequest(1007, Users.IMPORTER));
    
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
  }
  
}
