package org.col.admin.importer;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.admin.command.initdb.InitDbCmd;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.matching.NameIndexFactory;
import org.col.csl.AnystyleParserWrapper;
import org.col.db.PgSetupRule;
import org.junit.*;

@Ignore("manual import debugging")
public class ImportManagerDebugging {
  
  ImportManager importManager;
  CloseableHttpClient hc;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  private AdminServerConfig provideConfig() {
    AdminServerConfig cfg = new AdminServerConfig();
    cfg.gbif.syncFrequency = 0;
    cfg.importer.continousImportPolling = 0;
    cfg.importer.threads = 1;
    cfg.normalizer.archiveDir = Files.createTempDir();
    cfg.normalizer.scratchDir = Files.createTempDir();
    cfg.anystyle.baseUrl = "http://localhost:4567";
    cfg.db.host = "localhost";
    cfg.db.database = "colplus";
    cfg.db.user = "postgres";
    cfg.db.password = "postgres";
    return cfg;
  }

  @Before
  public void init() throws Exception {
    MetricRegistry metrics = new MetricRegistry();

    final AdminServerConfig cfg = provideConfig();
    InitDbCmd.execute(cfg);

    final CloseableHttpClient hc = new HttpClientBuilder(metrics)
        .using(cfg.client)
        .build("local");
    AnystyleParserWrapper anystyle = new AnystyleParserWrapper(hc, cfg.anystyle, metrics);
    importManager = new ImportManager(cfg, metrics, hc, PgSetupRule.getSqlSessionFactory(), anystyle, NameIndexFactory.passThru());
    importManager.start();
  }

  @After
  public void shutdown() throws Exception {
    importManager.stop();
    hc.close();
  }

  @Test
  public void debugImport() throws Exception {
    importManager.submit(11, true);
    Thread.sleep(1000);
    while (!importManager.hasEmptyQueue()) {
      Thread.sleep(1000);
    }
  }
}