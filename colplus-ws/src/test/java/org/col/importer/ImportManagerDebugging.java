package org.col.importer;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;

import org.apache.http.impl.client.CloseableHttpClient;
import org.col.WsServerConfig;
import org.col.api.vocab.Users;
import org.col.command.initdb.InitDbCmd;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.col.es.IndexConfig;
import org.col.es.name.index.NameUsageIndexService;
import org.col.img.ImageServiceFS;
import org.col.matching.NameIndexFactory;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.dropwizard.client.HttpClientBuilder;

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
    new InitDbCmd().execute(cfg);
    
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
    importManager.submit(new ImportRequest(2020, Users.IMPORTER));
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
  }
}
