package life.catalogue.importer;

import life.catalogue.WsServerConfig;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.codahale.metrics.MetricRegistry;
import com.google.common.eventbus.EventBus;
import com.google.common.io.Files;

import io.dropwizard.client.HttpClientBuilder;
import jakarta.validation.Validator;

@Ignore("manual import debugging")
@RunWith(MockitoJUnitRunner.class)
public class ImportManagerDebugging {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;

  ImportManager importManager;
  CloseableHttpClient hc;
  @Mock
  JobExecutor jobExecutor;
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
    cfg.importer.continuous.polling = 0;
    cfg.importer.threads = 3;
    cfg.normalizer.archiveDir = Files.createTempDir();
    cfg.normalizer.scratchDir = Files.createTempDir();
    cfg.db.host = "localhost";
    cfg.db.database = "colplus";
    cfg.db.user = "postgres";
    cfg.db.password = "postgres";
    cfg.es.hosts = "localhost";
    cfg.es.ports = "9200";
    
    return cfg;
  }
  
  @Before
  public void init() throws Exception {
    MetricRegistry metrics = new MetricRegistry();
    
    final WsServerConfig cfg = provideConfig();
    //new InitDbCmd().execute(cfg);

    NameUsageIndexService indexService = NameUsageIndexService.passThru();
    NameDao nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, indexService, validator);
    SectorDao sDao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), indexService, tDao, validator);
    DecisionDao dDao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    var diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    DatasetDao datasetDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, diDao, validator);

    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    importManager = new ImportManager(cfg, metrics, hc, new EventBus("test-bus"), SqlSessionFactoryRule.getSqlSessionFactory(),
        NameIndexFactory.build(NamesIndexConfig.memory(1024), SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started(),
      diDao, datasetDao, sDao, dDao, indexService, new ImageServiceFS(cfg.img, null), jobExecutor, validator, null);
    importManager.start();
  }
  
  @After
  public void shutdown() throws Exception {
    importManager.stop();
    hc.close();
  }
  
  /**
   * Try with 3 small parallel datasets
   */
  @Test
  public void debugParallel() throws Exception {
    importManager.submit(ImportRequest.external(1000, Users.IMPORTER));
    importManager.submit(ImportRequest.external(1006, Users.IMPORTER));
    importManager.submit(ImportRequest.external(1007, Users.IMPORTER));
    
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
  }
  
  @Test
  public void debugTaxonworks() throws Exception {
    DatasetWithSettings d = create(DataFormat.COLDP, "https://sfg.taxonworks.org/downloads/15/download_file", "TW Test");
    importManager.submit(ImportRequest.external(d.getKey(), Users.IMPORTER));
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
    System.out.println("Done");
  }

  @Test
  public void debugDsmz() throws Exception {
    DatasetWithSettings d = create(DataFormat.DWCA, "http://rs.gbif.org/datasets/dsmz.zip", "DSMZ");
    importManager.submit(ImportRequest.external(d.getKey(), Users.IMPORTER));
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
    System.out.println("Done");
  }

  @Test
  public void debugUpload() throws Exception {
    DatasetWithSettings d = create(DataFormat.DWCA, null,"Upload test");
    File f = new File("/Users/markus/Desktop/testUnassigned.txt");
    InputStream data = new FileInputStream(f);
    importManager.upload(d.getKey(), data, true, f.getName(), "tsv", TestEntityGenerator.USER_ADMIN);

    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
    System.out.println("Done");
  }

  private DatasetWithSettings create(DataFormat format, String url, String title) {
    DatasetWithSettings d = new DatasetWithSettings();
    d.setType(DatasetType.OTHER);
    d.setTitle(title);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setDataFormat(format);
    if (url != null) {
      d.setDataAccess(URI.create(url));
    }
    TestEntityGenerator.setUser(d.getDataset());
    
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      s.getMapper(DatasetMapper.class).createAll(d);
      s.commit();
    }
    
    return d;
  }
}
