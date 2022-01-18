package life.catalogue.importer;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.vocab.*;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.release.ReleaseManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.NomCode;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Validator;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * This test needs access to http://rs.gbif.org/datasets/dsmz.zip !!!
 */
//@Ignore
@RunWith(MockitoJUnitRunner.class)
public class ImportManagerLiveTest {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManagerLiveTest.class);

  ImportManager importManager;
  CloseableHttpClient hc;
  DatasetImportDao diDao;
  @Mock
  ReleaseManager releaseManager;
  @Mock
  Validator validator;

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

    NameUsageIndexService indexService = NameUsageIndexService.passThru();
    NameDao nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, indexService, validator);
    SectorDao sDao = new SectorDao(PgSetupRule.getSqlSessionFactory(), indexService, tDao, validator);
    DecisionDao dDao = new DecisionDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    DatasetDao datasetDao = new DatasetDao(PgSetupRule.getSqlSessionFactory(), null,diDao, validator);

    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    importManager = new ImportManager(cfg, metrics, hc, PgSetupRule.getSqlSessionFactory(),
        NameIndexFactory.passThru(), datasetDao, sDao, dDao, indexService, new ImageServiceFS(cfg.img), releaseManager, validator);
    importManager.start();

    LOG.warn("Test initialized");
  }
  
  @After
  public void shutdown() throws Exception {
    LOG.warn("Shutting down test");
    importManager.stop();
    hc.close();
  }
  
  private DatasetWithSettings createExternal() {
    DatasetWithSettings d = new DatasetWithSettings();
    d.setType(DatasetType.TAXONOMIC);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle("Moss Bug Base");
    d.setCode(NomCode.BACTERIAL);
    d.setDataFormat(DataFormat.DWCA);
    d.setDataAccess(URI.create("http://rs.gbif.org/datasets/dsmz.zip"));
    d.setCreatedBy(TestDataRule.TEST_USER.getKey());
    d.setModifiedBy(TestDataRule.TEST_USER.getKey());
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true) ) {
      session.getMapper(DatasetMapper.class).createAll(d);
      session.commit();
    }
    return d;
  }
  
  @Test
  public void cancel() throws Exception {
    final DatasetWithSettings d1 = createExternal();
  
    LOG.warn("SUBMIT");
    importManager.submit(ImportRequest.external(d1.getKey(), Users.IMPORTER));
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
    
    final DatasetWithSettings d1 = createExternal();
    final DatasetWithSettings d2 = createExternal();
    final DatasetWithSettings d3 = createExternal();
    final DatasetWithSettings d4 = createExternal();
  
    importManager.submit(ImportRequest.external(d1.getKey(), Users.IMPORTER));
    Thread.sleep(50);
    assertTrue(importManager.hasEmptyQueue());
    assertTrue(importManager.hasRunning());
    ResultPage<DatasetImport> imports = diDao.list(new Page());
    assertEquals(1, imports.size());
    assertEquals(d1.getKey(), imports.getResult().get(0).getDatasetKey());
    assertEquals(1, imports.getResult().get(0).getAttempt());
    assertEquals(d1.getDataAccess(), imports.getResult().get(0).getDownloadUri());
  
    assertTrue(runningStates.contains( imports.getResult().get(0).getState() ));

    importManager.submit(ImportRequest.external(d2.getKey(), Users.IMPORTER));
    importManager.submit(ImportRequest.external(d3.getKey(), Users.IMPORTER));
    importManager.submit(new ImportRequest(d4.getKey(), Users.IMPORTER, false, true, false));
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
