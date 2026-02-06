package life.catalogue.importer;

import life.catalogue.TestConfigs;
import life.catalogue.TestUtils;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.importer.store.ImportStoreFactory;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TreeRepoRule;
import life.catalogue.matching.nidx.NameIndexFactory;

import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Validator;

import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
@Disabled @Ignore("require github raw to always work")
public class ImportJobIgnoredIT {
  private static final Logger LOG = LoggerFactory.getLogger(ImportJobIgnoredIT.class);

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

  private TestConfigs cfg;
  private ImportJob job;
  private DatasetWithSettings d;
  private NameUsageIndexService indexService;
  private SectorDao sDao;
  private DecisionDao dDao;
  private DatasetDao datasetDao;
  private ImportStoreFactory importStoreFactory;


  @Before
  public void init() throws Exception {
    cfg = TestConfigs.build();
    hc = HttpClients.createDefault();
    importStoreFactory = new ImportStoreFactory(cfg.normalizer);
    diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    indexService = NameUsageIndexService.passThru();
    NameDao nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, new ThumborService(new ThumborConfig()), indexService, null, validator);
    sDao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, tDao, validator);
    dDao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    datasetDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null,diDao, validator, TestUtils.mockedBroker());
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

  private void setupAndRun(DataFormat format, URI archive){
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
    run(false);
  }

  private void run (boolean force) {
    ImportRequest req = ImportRequest.external(d.getKey(), Users.TESTER, force);
    job = new ImportJob(req, d, cfg.importer, cfg.normalizer, cfg.doi, new DownloadUtil(hc), SqlSessionFactoryRule.getSqlSessionFactory(), importStoreFactory,
      NameIndexFactory.passThru(), validator, null,
      indexService, new ImageServiceFS(cfg.img, null), diDao, datasetDao, sDao, dDao, TestUtils.mockedBroker(), this::start, this::success, this::error);
    job.run();
  }

  @Test
  public void proxy() {
    setupAndRun(DataFormat.PROXY, Resources.uri("proxy/1011.yaml"));
  }
}