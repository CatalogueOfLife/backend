package org.col.admin.task.importer;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.admin.command.initdb.InitDbCmd;
import org.col.admin.config.AdminServerConfig;
import org.col.csl.AnystyleParserWrapper;
import org.col.db.mapper.PgSetupRule;
import org.junit.*;

//@Ignore("manual import debugging")
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
    cfg.anystyle.host = "localhost";
    cfg.db.host = "localhost";
    cfg.db.database = "colplus";
    cfg.db.user = "postgres";
    cfg.db.password = "postgres";
    return cfg;
  }

  @Before
  public void init() throws Exception {
    final AdminServerConfig cfg = provideConfig();
    InitDbCmd.execute(cfg);

    final CloseableHttpClient hc = new HttpClientBuilder(new MetricRegistry())
        .using(cfg.client)
        .build("local");
    AnystyleParserWrapper anystyle = new AnystyleParserWrapper(hc, cfg.anystyle);
    importManager = new ImportManager(cfg, hc, PgSetupRule.getSqlSessionFactory(), anystyle);
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
    while (!importManager.isIdle()) {
      Thread.sleep(1000);
    }
  }
}