package org.col.importer;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.col.WsServerConfig;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetOrigin;
import org.col.api.vocab.DatasetType;
import org.col.api.vocab.ImportState;
import org.col.api.vocab.Users;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.dao.DatasetImportDao;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.TestDataRule;
import org.col.img.ImageServiceFS;
import org.col.matching.NameIndexFactory;
import org.gbif.nameparser.api.NomCode;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.client.HttpClientBuilder;

import static org.junit.Assert.assertFalse;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * This test needs access to github !!!
 * It downloads the test coldp archive file sitting in the test resources from github!!!
 */
@Ignore
public class ImportManagerLiveTest {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManagerLiveTest.class);
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();
  
  ImportManager importManager;
  CloseableHttpClient hc;
  DatasetImportDao diDao;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  private static WsServerConfig provideConfig() {
    WsServerConfig cfg = new WsServerConfig();
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
    
    final WsServerConfig cfg = provideConfig();
    
    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    importManager = new ImportManager(cfg, metrics, hc, PgSetupRule.getSqlSessionFactory(), aNormalizer,
        NameIndexFactory.passThru(), null, new ImageServiceFS(cfg.img));
    importManager.start();
  
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    LOG.warn("Test initialized");
  }
  
  @After
  public void shutdown() throws Exception {
    LOG.warn("Shutting down test");
    importManager.stop();
    hc.close();
  }
  
  private Dataset createExternal() {
    Dataset d = new Dataset();
    d.setType(DatasetType.GLOBAL);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Moss Bug Base");
    d.setCode(NomCode.BACTERIAL);
    d.setDataFormat(DataFormat.DWCA);
    d.setDataAccess(URI.create("http://rs.gbif.org/datasets/dsmz.zip"));
    d.setCreatedBy(TestDataRule.TEST_USER.getKey());
    d.setModifiedBy(TestDataRule.TEST_USER.getKey());
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true) ) {
      session.getMapper(DatasetMapper.class).create(d);
      session.commit();
    }
    return d;
  }
  
  @Test
  public void cancel() throws Exception {
    final Dataset d1 = createExternal();
  
    LOG.warn("SUBMIT");
    importManager.submit(new ImportRequest(d1.getKey(), Users.IMPORTER));
    Thread.sleep(50);
    
    LOG.warn("CHECK");
    assertTrue(importManager.hasEmptyQueue());
    assertTrue(importManager.hasRunning());
 
    // cancel
    LOG.warn("CANCEL");
    importManager.cancel(d1.getKey(), -1);
    TimeUnit.MILLISECONDS.sleep(500);
    LOG.warn("CHECK");
    assertTrue(importManager.hasEmptyQueue());
    assertFalse(importManager.hasRunning());
  }
  
  @Test
  public void submitAndCancel() throws Exception {
    final List<ImportState> runningStates = ImportState.runningStates();
    
    final Dataset d1 = createExternal();
    final Dataset d2 = createExternal();
    final Dataset d3 = createExternal();
    final Dataset d4 = createExternal();
  
    importManager.submit(new ImportRequest(d1.getKey(), Users.IMPORTER));
    Thread.sleep(50);
    assertTrue(importManager.hasEmptyQueue());
    assertTrue(importManager.hasRunning());
    ResultPage<DatasetImport> imports = diDao.list(new Page());
    assertEquals(1, imports.size());
    assertEquals(d1.getKey(), imports.getResult().get(0).getDatasetKey());
    assertEquals(1, imports.getResult().get(0).getAttempt());
    assertEquals(d1.getDataAccess(), imports.getResult().get(0).getDownloadUri());
  
    assertTrue(runningStates.contains( imports.getResult().get(0).getState() ));

    importManager.submit(new ImportRequest(d2.getKey(), Users.IMPORTER));
    importManager.submit(new ImportRequest(d3.getKey(), Users.IMPORTER));
    importManager.submit(new ImportRequest(d4.getKey(), Users.IMPORTER, false, true));
    importManager.hasRunning();
    Thread.sleep(50);
    imports = diDao.list(new Page());
    assertEquals(2, imports.size());
    assertEquals(d2.getKey(), imports.getResult().get(0).getDatasetKey());
    assertEquals(1, imports.getResult().get(0).getAttempt());
    assertEquals(d2.getDataAccess(), imports.getResult().get(0).getDownloadUri());
    assertEquals(d1.getKey(), imports.getResult().get(1).getDatasetKey());
    
    assertFalse(importManager.hasEmptyQueue());
    List<ImportRequest> queue = importManager.queue();
    // d4 has priority
    assertEquals((int)d4.getKey(), queue.get(0).datasetKey);
  
    // cancel d4
    importManager.cancel(d4.getKey(), -1);
    Thread.sleep(100);
    queue = importManager.queue();
    assertEquals(1, queue.size());
    assertEquals((int)d3.getKey(), queue.get(0).datasetKey);
  
    imports = diDao.list(new Page());
    assertEquals(d2.getKey(), imports.getResult().get(0).getDatasetKey());
    assertEquals(d1.getKey(), imports.getResult().get(1).getDatasetKey());
  
    // cancel d1
    importManager.cancel(d1.getKey(), -1);
    TimeUnit.SECONDS.sleep(1);
    assertTrue(importManager.hasEmptyQueue());
    imports = diDao.list(new Page());
    assertEquals(3, imports.size());
    assertEquals(d3.getKey(), imports.getResult().get(0).getDatasetKey());
    assertTrue(runningStates.contains(imports.getResult().get(0).getState()));
    
    assertEquals(d2.getKey(), imports.getResult().get(1).getDatasetKey());
    assertTrue(runningStates.contains(imports.getResult().get(1).getState()));

    assertEquals(d1.getKey(), imports.getResult().get(2).getDatasetKey());
    assertEquals(ImportState.CANCELED, imports.getResult().get(2).getState());
  }
  
}
