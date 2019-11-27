package org.col.importer;

import java.net.URI;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.col.WsServerConfig;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetOrigin;
import org.col.api.vocab.DatasetType;
import org.col.api.vocab.Users;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetMapper;
import org.col.es.name.index.NameUsageIndexService;
import org.col.img.ImageServiceFS;
import org.col.matching.NameIndexFactory;
import org.elasticsearch.client.RestClient;
import org.junit.*;

@Ignore("manual import debugging")
public class ImportManagerDebugging {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();

  ImportManager importManager;
  CloseableHttpClient hc;
  RestClient esClient;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  private static WsServerConfig provideConfig() {
    WsServerConfig cfg = new WsServerConfig();
    cfg.gbif.syncFrequency = 0;
    cfg.importer.continousImportPolling = 0;
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
    
    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    importManager = new ImportManager(cfg, metrics, hc, PgSetupRule.getSqlSessionFactory(), aNormalizer,
        NameIndexFactory.passThru(), NameUsageIndexService.passThru(), new ImageServiceFS(cfg.img));
    importManager.start();
  }
  
  @After
  public void shutdown() throws Exception {
    importManager.stop();
    hc.close();
    esClient.close();
  }
  
  /**
   * Try with 3 small parallel datasets
   */
  @Test
  public void debugParallel() throws Exception {
    importManager.submit(new ImportRequest(1000, Users.IMPORTER));
    importManager.submit(new ImportRequest(1006, Users.IMPORTER));
    importManager.submit(new ImportRequest(1007, Users.IMPORTER));
    
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
  }
  
  @Test
  public void debugImport() throws Exception {
    Dataset d = create(DataFormat.COLDP, "https://sfg.taxonworks.org/downloads/15/download_file", "TW Test");
    // do it again as key 1 is problematic
    d = create(DataFormat.COLDP, "https://sfg.taxonworks.org/downloads/15/download_file", "TW Test");
    System.out.println("Submitting " + d);
    importManager.submit(new ImportRequest(d.getKey(), Users.IMPORTER));
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
    System.out.println("Done");
  }
  
  private Dataset create(DataFormat format, String url, String title) {
    Dataset d = new Dataset();
    d.setType(DatasetType.OTHER);
    d.setTitle(title);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setDataFormat(format);
    d.setDataAccess(URI.create(url));
    TestEntityGenerator.setUser(d);
    
    try (SqlSession s = PgSetupRule.getSqlSessionFactory().openSession()) {
      s.getMapper(DatasetMapper.class).create(d);
      s.commit();
    }
    
    return d;
  }
}
