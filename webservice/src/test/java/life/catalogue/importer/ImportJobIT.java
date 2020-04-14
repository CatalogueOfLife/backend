package life.catalogue.importer;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.release.ReleaseManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ImportJobIT {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManagerLiveTest.class);

  CloseableHttpClient hc;
  DatasetImportDao diDao;
  @Mock
  ReleaseManager releaseManager;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  private WsServerConfig cfg;
  private ImportJob job;
  private Dataset d;

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
    cfg = provideConfig();
    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
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

  private void setupNrun(DataFormat format, String access){
    URI archive = Resources.uri(access);
    d = new Dataset();
    d.setType(DatasetType.OTHER);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setCreatedBy(TestDataRule.TEST_USER.getKey());
    d.setModifiedBy(TestDataRule.TEST_USER.getKey());
    d.setTitle("Test import");
    d.setDataFormat(format);
    d.setDataAccess(archive);
    try(SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()){
      session.getMapper(DatasetMapper.class).create(d);
      session.commit();
    }

    ImportRequest req = new ImportRequest(d.getKey(), Users.TESTER, false, false, false);
    job = new ImportJob(req, d, cfg, new DownloadUtil(hc), PgSetupRule.getSqlSessionFactory(), NameIndexFactory.passThru(),
      null, new ImageServiceFS(cfg.img), this::start, this::success, this::error);

  }

  @Test
  public void proxy() {
    setupNrun(DataFormat.PROXY, "proxy/1011.yaml");
    job.run();
  }
}