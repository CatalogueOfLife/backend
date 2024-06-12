package life.catalogue.importer;

import life.catalogue.WsServerConfig;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.Resources;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.matching.NameIndexFactory;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.io.Files;

import io.dropwizard.client.HttpClientBuilder;
import jakarta.validation.Validator;

import static org.junit.Assert.*;

/**
 * Simple unit tests
 */
@RunWith(MockitoJUnitRunner.class)
public class ImportManagerTest {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManagerTest.class);

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  ImportManager manager;
  int datasetKey;

  CloseableHttpClient hc;
  @Mock
  JobExecutor jobExecutor;
  @Mock
  ImageServiceFS imgService;
  @Mock
  Validator validator;

  private static WsServerConfig provideConfig() {
    WsServerConfig cfg = new WsServerConfig();
    cfg.gbif.syncFrequency = 0;
    cfg.importer.continuous.polling = 0;
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
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = new Dataset();
      d.setTitle("upload test");
      d.setOrigin(DatasetOrigin.PROJECT);
      d.setType(DatasetType.OTHER);
      d.setCreatedBy(Users.TESTER);
      d.setModifiedBy(Users.TESTER);
      dm.create(d);
      datasetKey = d.getKey();
    }

    NameUsageIndexService indexService = NameUsageIndexService.passThru();
    NameDao nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, indexService, validator);
    SectorDao sDao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, tDao, validator);
    DecisionDao dDao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    var diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), new File("/tmp"));
    DatasetDao datasetDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null,diDao, validator);

    MetricRegistry metrics = new MetricRegistry();
    final WsServerConfig cfg = provideConfig();
    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    manager = new ImportManager(cfg, metrics, hc, new EventBus("test-bus"), SqlSessionFactoryRule.getSqlSessionFactory(), NameIndexFactory.passThru(),
      diDao, datasetDao, sDao, dDao, indexService, imgService, jobExecutor, validator, null);
    manager.start();
  }

  @After
  public void shutdown() throws Exception {
    LOG.warn("Shutting down test");
    manager.stop();
    hc.close();
  }

  @Test
  public void upload() throws Exception {
    final String resName = "dwca/1/taxa.txt";
    assertFalse(manager.hasRunning());
    try {
      manager.upload(Datasets.COL, Resources.stream(resName), true, "taxa.txt", "txt", TestEntityGenerator.USER_ADMIN);
      fail("Cannot upload to col draft");
    } catch (IllegalArgumentException e) {
      // expected, its the draft
    }
    manager.upload(datasetKey, Resources.stream(resName), true, "taxa.txt", "txt", TestEntityGenerator.USER_ADMIN);
    TimeUnit.SECONDS.sleep(2);
    assertTrue(manager.hasRunning());
  }

  @Test
  public void uploadXls() throws Exception {
    InputStream data = Resources.stream("xls/Pterophoroidea.xlsx");
    assertFalse(manager.hasRunning());
    try {
      manager.uploadXls(Datasets.COL, data, TestEntityGenerator.USER_ADMIN);
      fail("Cannot upload to col draft");
    } catch (IllegalArgumentException e) {
      // expected, its the draft
    }
    manager.uploadXls(datasetKey, data, TestEntityGenerator.USER_ADMIN);
    TimeUnit.SECONDS.sleep(2);
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
