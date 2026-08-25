package life.catalogue.importer;

import life.catalogue.TestConfigs;
import life.catalogue.TestUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.Resources;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.nidx.NameIndexFactory;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import com.sun.net.httpserver.HttpServer;

import jakarta.validation.Validator;

import static org.junit.Assert.*;

/**
 * Simple unit tests
 */
@RunWith(MockitoJUnitRunner.class)
public class ImportManagerTest {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManagerTest.class);
  // distinctive values so we can tell them apart from any config default
  private static final int IMPORT_THREADS = 3;
  private static final int IMPORT_QUEUE = 137;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  ImportManager manager;
  JobExecutor jobExecutor;
  int datasetKey;

  CloseableHttpClient hc;
  @Mock
  UserCrudDao udao;
  @Mock
  ImageServiceFS imgService;
  @Mock
  Validator validator;

  @Before
  public void init() throws Exception {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = new Dataset();
      d.setTitle("upload test");
      d.setOrigin(DatasetOrigin.EXTERNAL);
      d.setType(DatasetType.OTHER);
      d.setCreatedBy(Users.TESTER);
      d.setModifiedBy(Users.TESTER);
      dm.create(d);
      datasetKey = d.getKey();
    }

    var broker = TestUtils.mockedBroker();
    NameUsageIndexService indexService = NameUsageIndexService.passThru();
    NameDao nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, new ThumborService(new ThumborConfig()), indexService, null, validator);
    SectorDao sDao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, tDao, validator);
    DecisionDao dDao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    var diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), new File("/tmp"));
    DatasetDao datasetDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null,diDao, validator, broker);

    MetricRegistry metrics = new MetricRegistry();
    final TestConfigs cfg = TestConfigs.build();
    hc = HttpClients.createDefault();
    doReturn(TestEntityGenerator.USER_ADMIN).when(udao).get(any());
    JobConfig jobCfg = JobConfig.withThreads(2);
    jobCfg.importThreads = IMPORT_THREADS;
    jobCfg.importQueue = IMPORT_QUEUE;
    jobExecutor = new JobExecutor(jobCfg, metrics, null, udao, null);
    manager = new ImportManager(cfg.importer, cfg.normalizer, cfg.doi, metrics, hc, broker, SqlSessionFactoryRule.getSqlSessionFactory(), NameIndexFactory.passThru(),
      diDao, datasetDao, sDao, dDao, indexService, imgService, jobExecutor, validator, null, null, null);
    manager.start();
  }

  @After
  public void shutdown() throws Exception {
    LOG.warn("Shutting down test");
    manager.stop();
    jobExecutor.stop();
    hc.close();
  }

  /**
   * The import lane is sized by the job config alone - the importer config must not carry a second,
   * competing thread/queue setting that silently wins or loses.
   */
  @Test
  public void laneSizingComesFromJobConfig() {
    assertEquals(IMPORT_QUEUE, manager.maxQueue());
  }

  @Test
  public void upload() throws Exception {
    final String resName = "dwca/1/taxa.txt";
    assertFalse(manager.hasRunning());
    try {
      manager.upload(Datasets.COL, Resources.stream(resName), true, "taxa.txt", "txt", TestEntityGenerator.USER_ADMIN, null);
      fail("Cannot upload to col draft");
    } catch (IllegalArgumentException e) {
      // expected, its the draft
    }
    manager.upload(datasetKey, Resources.stream(resName), true, "taxa.txt", "txt", TestEntityGenerator.USER_ADMIN, null);
    TimeUnit.MILLISECONDS.sleep(100);
    assertTrue(manager.hasRunning());
  }

  /**
   * Full wiring test for the optional completion callback of https://github.com/CatalogueOfLife/backend/issues/1552.
   * Whether the import itself succeeds or fails does not matter - both are terminal and must notify the callback.
   */
  @Test
  public void uploadCallback() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> received = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    server.createContext("/hook", ex -> {
      try (InputStream in = ex.getRequestBody()) {
        received.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
      ex.sendResponseHeaders(200, -1);
      ex.close();
      latch.countDown();
    });
    server.start();
    URI callback = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
    try {
      manager.upload(datasetKey, Resources.stream("dwca/1/taxa.txt"), true, "taxa.txt", "txt", TestEntityGenerator.USER_ADMIN, callback);
      assertTrue("import completion callback was never fired", latch.await(120, TimeUnit.SECONDS));
    } finally {
      server.stop(0);
    }
    LOG.info("Callback received: {}", received.get());
    assertNotNull(received.get());
  }

  /**
   * Cancelling is terminal for the client that submitted the request, so it must notify the callback too.
   * Regression test: cancel() dropped the job from the futures map before the job's error handler ran,
   * which is where the callback used to look up its DatasetImport.
   */
  @Test
  public void cancelledUploadCallback() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    CountDownLatch latch = new CountDownLatch(1);
    server.createContext("/hook", ex -> {
      ex.sendResponseHeaders(200, -1);
      ex.close();
      latch.countDown();
    });
    server.start();
    URI callback = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
    try {
      manager.upload(datasetKey, Resources.stream("dwca/1/taxa.txt"), true, "taxa.txt", "txt", TestEntityGenerator.USER_ADMIN, callback);
      // wait until the job left the queue and is actually executing, then cancel it
      while (manager.isRunning(datasetKey) && manager.queue().stream().anyMatch(r -> r.datasetKey == datasetKey)) {
        TimeUnit.MILLISECONDS.sleep(10);
      }
      manager.cancel(datasetKey, Users.TESTER);
      assertTrue("cancelled import must still notify the completion callback", latch.await(60, TimeUnit.SECONDS));
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void uploadXls() throws Exception {
    InputStream data = Resources.stream("xls/Pterophoroidea.xlsx");
    assertFalse(manager.hasRunning());
    try {
      manager.uploadXls(Datasets.COL, data, TestEntityGenerator.USER_ADMIN, null);
      fail("Cannot upload to col draft");
    } catch (IllegalArgumentException e) {
      // expected, its the draft
    }
    manager.uploadXls(datasetKey, data, TestEntityGenerator.USER_ADMIN, null);
    TimeUnit.MILLISECONDS.sleep(100);
    assertTrue(manager.hasRunning());
  }

  @Test
  public void limit() throws Exception {
    List<Integer> list = new ArrayList<>(Arrays.asList(new Integer[]{1,2,3,45,5,6}));
  
    ImportManager.limit(list, 10);
    assertEquals(Lists.newArrayList(1,2,3,45,5,6), list);

    ImportManager.limit(list, 4);
    assertEquals(Lists.newArrayList(1,2,3,45), list);
  }
  
  @Test
  public void offset() throws Exception {
    List<Integer> list = new ArrayList<>(Arrays.asList(new Integer[]{1,2,3,45,5,6}));
    
    ImportManager.removeOffset(list, 1);
    assertEquals(Lists.newArrayList(2,3,45,5,6), list);
  
    ImportManager.removeOffset(list, 4);
    assertEquals(Lists.newArrayList(6), list);
  
    ImportManager.removeOffset(list, 4);
    assertEquals(Lists.newArrayList(), list);
  }
  
}
