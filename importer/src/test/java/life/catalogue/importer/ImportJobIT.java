package life.catalogue.importer;

import life.catalogue.TestConfigs;
import life.catalogue.TestUtils;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es2.indexing.NameUsageIndexService;
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
import java.net.URL;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Validator;

import static org.junit.Assert.*;

/**
 * Integration test for imports with an embedded nginx serving external dataset archives.
 * This allows to verify MD5 hashes, unmodified archives, last symlinks and forced imports.
 */
@RunWith(MockitoJUnitRunner.class)
public class ImportJobIT {
  private static final Logger LOG = LoggerFactory.getLogger(ImportJobIT.class);

  CloseableHttpClient hc;
  DatasetImportDao diDao;
  @Mock
  Validator validator;

  @ClassRule
  public static NginxRule nginxRule = new NginxRule();

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
    cfg.removeCfgDirs();
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
  public void plaziUnchanged() {
    URI uri = nginxRule.getArchive(DataFormat.DWCA);
    setupAndRun(DataFormat.DWCA, uri);
    verifyLatest(1);

    run(false);
    verifyLatest(1);

    run(false);
    verifyLatest(1);

    run(true); // this creates a new import #2
    verifyLatest(2);

    run(false);
    verifyLatest(2);

    // try another archive with different md5
    d.setDataFormat(DataFormat.COLDP);
    d.setDataAccess(nginxRule.getArchive(DataFormat.COLDP));
    try(SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)){
      session.getMapper(DatasetMapper.class).updateSettings(d.getKey(), d.getSettings(), Users.TESTER);
    }
    run(false);
    verifyLatest(3);

    run(false);
    verifyLatest(3);

    run(false);
    verifyLatest(3);
  }

  private void verifyLatest(int attempt) {
    var dataset = datasetDao.get(d.getKey());
    assertEquals(attempt, (int) dataset.getAttempt());
    var imp = diDao.getLast(d.getKey());
    assertEquals((int) dataset.getAttempt(), imp.getAttempt());
    assertEquals(ImportState.FINISHED, imp.getState());

    var archive = cfg.normalizer.archive(d.getKey(), attempt);
    assertTrue(archive.exists());

    var archiveNext = cfg.normalizer.archive(d.getKey(), attempt+1);
    assertFalse(archiveNext.exists());
  }
}