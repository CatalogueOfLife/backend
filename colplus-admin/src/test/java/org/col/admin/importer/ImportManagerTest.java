package org.col.admin.importer;

import java.net.URI;
import java.util.Queue;
import java.util.Set;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.matching.NameIndexFactory;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.*;
import org.col.db.PgSetupRule;
import org.col.db.dao.DatasetImportDao;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.InitMybatisRule;
import org.col.img.ImageService;
import org.junit.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * This test needs access to github !!!
 * It downloads the test coldp archive file sitting in the test resources from github!!!
 */
@Ignore
public class ImportManagerTest {
  
  ImportManager importManager;
  CloseableHttpClient hc;
  DatasetImportDao diDao;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.empty();
  
  private static AdminServerConfig provideConfig() {
    AdminServerConfig cfg = new AdminServerConfig();
    cfg.gbif.syncFrequency = 0;
    cfg.importer.continousImportPolling = 0;
    cfg.importer.threads = 2;
    // wait for half a minute before completing an import to run assertions
    cfg.importer.wait = 30;
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
  
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory());
  }
  
  @After
  public void shutdown() throws Exception {
    importManager.stop();
    hc.close();
  }
  
  private Dataset createExternal() {
    Dataset d = new Dataset();
    d.setType(DatasetType.GLOBAL);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Test dataset");
    d.setDataFormat(DataFormat.COLDP);
    d.setDataAccess(URI.create("https://github.com/Sp2000/colplus-backend/raw/master/colplus-admin/src/test/resources/coldp/test.zip"));
    d.setCreatedBy(InitMybatisRule.TEST_USER.getKey());
    d.setModifiedBy(InitMybatisRule.TEST_USER.getKey());
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(false) ) {
      session.getMapper(DatasetMapper.class).create(d);
      session.commit();
    }
    return d;
  }
  
  @Test
  public void submitAndCancel() throws Exception {
    final Dataset d1 = createExternal();
    final Dataset d2 = createExternal();
    final Dataset d3 = createExternal();
    final Dataset d4 = createExternal();
  
    importManager.submit(new ImportRequest(d1.getKey(), Users.IMPORTER));
    Thread.sleep(100);
    assertTrue(importManager.hasEmptyQueue());
    assertTrue(importManager.hasRunning());
    ResultPage<DatasetImport> imports = diDao.list(new Page());
    assertEquals(1, imports.size());
    assertEquals(d1.getKey(), imports.getResult().get(0).getDatasetKey());
    assertEquals((Integer)1, imports.getResult().get(0).getAttempt());
    assertEquals(d1.getDataAccess(), imports.getResult().get(0).getDownloadUri());
  
    Set<ImportState> runningStates = Sets.newHashSet(ImportState.DOWNLOADING, ImportState.PROCESSING, ImportState.INSERTING);
    assertTrue(runningStates.contains( imports.getResult().get(0).getState() ));

    importManager.submit(new ImportRequest(d2.getKey(), Users.IMPORTER));
    importManager.submit(new ImportRequest(d3.getKey(), Users.IMPORTER));
    importManager.submit(new ImportRequest(d4.getKey(), Users.IMPORTER, false, true));
    Thread.sleep(100);
    importManager.hasRunning();
    imports = diDao.list(new Page());
    assertEquals(2, imports.size());
    assertEquals(d2.getKey(), imports.getResult().get(0).getDatasetKey());
    assertEquals((Integer)1, imports.getResult().get(0).getAttempt());
    assertEquals(d2.getDataAccess(), imports.getResult().get(0).getDownloadUri());
    assertEquals(d1.getKey(), imports.getResult().get(1).getDatasetKey());
    
    assertFalse(importManager.hasEmptyQueue());
    Queue<ImportRequest> queue = importManager.queue();
    // d4 has priority
    assertEquals((int)d4.getKey(), queue.peek().datasetKey);
  
    // cancel d4
    importManager.cancel(d4.getKey());
    Thread.sleep(100);
    queue = importManager.queue();
    assertEquals(1, queue.size());
    assertEquals((int)d3.getKey(), queue.peek().datasetKey);
  
    imports = diDao.list(new Page());
    assertEquals(d2.getKey(), imports.getResult().get(0).getDatasetKey());
    assertEquals(d1.getKey(), imports.getResult().get(1).getDatasetKey());
  
    // cancel d1
    importManager.cancel(d1.getKey());
    Thread.sleep(5000);
    assertTrue(importManager.hasEmptyQueue());
  }
  
}
