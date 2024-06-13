package life.catalogue.importer;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.matching.nidx.NameIndexFactory;

import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.eventbus.EventBus;
import com.google.common.io.Files;

import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.util.Duration;
import jakarta.validation.Validator;

import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class ImportJobIT {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManagerLiveTest.class);

  CloseableHttpClient hc;
  DatasetImportDao diDao;
  @Mock
  Validator validator;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();

  private WsServerConfig cfg;
  private ImportJob job;
  private DatasetWithSettings d;
  private NameUsageIndexService indexService;
  private SectorDao sDao;
  private DecisionDao dDao;
  private DatasetDao datasetDao;

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
    // http
    cfg.client.setTimeout(Duration.minutes(1));
    cfg.client.setConnectionTimeout(Duration.minutes(1));
    cfg.client.setConnectionRequestTimeout(Duration.minutes(1));
    return cfg;
  }

  @Before
  public void init() throws Exception {
    MetricRegistry metrics = new MetricRegistry();
    cfg = provideConfig();
    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    indexService = NameUsageIndexService.passThru();
    NameDao nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, indexService, validator);
    sDao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, tDao, validator);
    dDao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    datasetDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null,diDao, validator);
    LOG.warn("Test initialized");
  }

  @After
  public void shutdown() throws Exception {
    LOG.warn("Shutting down test");
    hc.close();
  }

  void start(){
    LOG.info("Start");
  }

  void success(ImportRequest req){
    LOG.info("Success");
  }

  void error(ImportRequest req, Exception e){
    LOG.error("Failed", e);
    fail("Import Error");
  }

  private void setupAndRun(DataFormat format, String access){
    URI archive = Resources.uri(access);
    d = new DatasetWithSettings();
    d.setType(DatasetType.OTHER);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setCreatedBy(TestDataRule.TEST_USER.getKey());
    d.setModifiedBy(TestDataRule.TEST_USER.getKey());
    d.setTitle("Test import");
    d.setDataFormat(format);
    d.setDataAccess(archive);
    try(SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()){
      session.getMapper(DatasetMapper.class).createAll(d);
      session.commit();
    }

    ImportRequest req = ImportRequest.external(d.getKey(), Users.TESTER);
    job = new ImportJob(req, d, cfg, new DownloadUtil(hc), SqlSessionFactoryRule.getSqlSessionFactory(), NameIndexFactory.passThru(), validator, null,
      indexService, new ImageServiceFS(cfg.img, null), diDao, datasetDao, sDao, dDao, new EventBus("test-bus"), this::start, this::success, this::error);

  }

  @Test
  @Ignore("require github raw to always work")
  public void proxy() {
    setupAndRun(DataFormat.PROXY, "proxy/1011.yaml");
    job.run();
  }
}