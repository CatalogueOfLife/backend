package life.catalogue;

import life.catalogue.command.MatcherStoreBuilder;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.config.MatchingConfig;
import life.catalogue.es.EsSetupRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.io.File;
import java.io.Writer;
import java.nio.file.Files;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Boots a real {@link WsBundleServer} against a postgres and an elasticsearch container and verifies the two
 * things that make a bundle a bundle: every read resource is reachable without knowing the dataset key, and
 * elasticsearch is mandatory and gets filled from the bundled database on first boot.
 */
public class WsBundleServerIT {
  static final int KEY = TestDataRule.APPLE.key;

  @ClassRule
  public static final SqlSessionFactoryRule pg = new PgSetupRule();

  @ClassRule
  public static final EsSetupRule es = new EsSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void keylessRoutingAndFirstBootIndexing() throws Exception {
    File data = Files.createTempDirectory("col-bundle-it").toFile();
    buildMatcherStore(data);
    var support = support(configFile(data, true));
    support.before();
    Client client = ClientBuilder.newClient();
    try {
      final String api = "http://localhost:" + support.getLocalPort();

      // the single dataset, without a key
      var dataset = get(client, api + "/dataset");
      assertEquals(200, dataset.getStatus());
      assertTrue("expected the single release", dataset.readEntity(String.class).contains("\"key\":" + KEY));

      // keyless dataset scoped reads
      assertEquals(200, get(client, api + "/tree").getStatus());
      assertEquals(200, get(client, api + "/taxon").getStatus());
      assertEquals(200, get(client, api + "/name").getStatus());
      assertEquals(200, get(client, api + "/nameusage").getStatus());

      // ... and the keyed forms still work
      assertEquals(200, get(client, api + "/dataset/" + KEY + "/tree").getStatus());
      assertEquals(200, get(client, api + "/dataset/" + KEY + "/taxon").getStatus());

      // genuinely global resources are untouched by the rewrite
      assertEquals(200, get(client, api + "/parser/name?q=Abies%20alba").getStatus());
      assertEquals(200, get(client, api + "/version").getStatus());
      assertEquals(200, get(client, api + "/vocab").getStatus());

      // OpenRefine reconciliation, keyless
      var manifest = get(client, api + "/reconcile");
      assertEquals(200, manifest.getStatus());
      assertTrue(manifest.readEntity(String.class).contains("defaultTypes"));

      // matching is global and already keyless
      assertEquals(200, get(client, api + "/match/nameusage?q=Larus%20fuscus").getStatus());

      // first boot indexing filled elastic from the bundled postgres
      assertTrue("elastic was never filled from the bundled database", awaitIndexed(client, api));

    } finally {
      client.close();
      support.after();
      org.apache.commons.io.FileUtils.deleteQuietly(data);
    }
  }

  @Test
  public void elasticIsMandatory() throws Exception {
    File data = Files.createTempDirectory("col-bundle-it-noes").toFile();
    buildMatcherStore(data);
    var support = support(configFile(data, false));
    try {
      support.before();
      fail("a bundle must not start without elasticsearch");
    } catch (Exception e) {
      assertTrue("unexpected: " + e, rootMessage(e).contains("requires Elasticsearch"));
    } finally {
      try {
        support.after();
      } catch (Exception ignored) {
        // the app never came up
      }
      org.apache.commons.io.FileUtils.deleteQuietly(data);
    }
  }

  private static String rootMessage(Throwable t) {
    StringBuilder sb = new StringBuilder();
    while (t != null) {
      sb.append(t.getMessage()).append('\n');
      t = t.getCause();
    }
    return sb.toString();
  }

  private DropwizardTestSupport<WsBundleServerConfig> support(File cfg) {
    return new DropwizardTestSupport<>(WsBundleServer.class, cfg.getAbsolutePath());
  }

  private Response get(Client client, String uri) {
    return client.target(uri).request().get();
  }

  /**
   * The bundle indexes its release into an empty index on a daemon thread, so give it a moment.
   */
  private boolean awaitIndexed(Client client, String api) throws Exception {
    for (int i = 0; i < 120; i++) {
      var r = get(client, api + "/nameusage/search?q=Larus&limit=1");
      if (r.getStatus() == 200 && r.readEntity(String.class).contains("\"total\":") ) {
        var r2 = get(client, api + "/nameusage/search?limit=1");
        String body = r2.readEntity(String.class);
        if (r2.getStatus() == 200 && !body.contains("\"total\":0")) {
          return true;
        }
      }
      Thread.sleep(500);
    }
    return false;
  }

  private void buildMatcherStore(File data) throws Exception {
    NamesIndexConfig nCfg = NamesIndexConfig.file(new File(data, "nidx"), 32);
    nCfg.verification = false;
    MatchingConfig mCfg = new MatchingConfig();
    mCfg.storageDir = new File(data, "matcher");
    mCfg.uploadDir = new File(data, "upload");
    mCfg.mkdirs();
    MatcherStoreBuilder.build(KEY, nCfg, mCfg, SqlSessionFactoryRule.getSqlSessionFactory(), SqlSessionFactoryRule.getSqlSessionFactory());
  }

  private File configFile(File data, boolean withEs) throws Exception {
    File f = new File(data, "config.yaml");
    try (Writer w = UTF8IoUtils.writerFromFile(f)) {
      w.write("releaseKey: " + KEY + "\n");
      w.write("indexOnStart: true\n");
      w.write("db:\n");
      YamlUtils.write(SqlSessionFactoryRule.getCfg(), 2, w);
      if (withEs) {
        w.write("\nes:\n");
        YamlUtils.write(es.getEsConfig(), 2, w);
      }
      w.write("\nmatching:\n  storageDir: " + new File(data, "matcher").getAbsolutePath()
        + "\n  uploadDir: " + new File(data, "upload").getAbsolutePath() + "\n");
      w.write("namesIndex:\n  file: " + new File(data, "nidx").getAbsolutePath()
        + "\n  verification: false\n");
      w.write("metricsRepo: " + new File(data, "metrics").getAbsolutePath() + "\n");
      w.write("img:\n  repo: " + new File(data, "img").getAbsolutePath()
        + "\n  archive: " + new File(data, "img/archive").getAbsolutePath() + "\n");
      w.write("job:\n  logDir: " + new File(data, "joblog").getAbsolutePath()
        + "\n  downloadDir: " + new File(data, "jobs").getAbsolutePath() + "\n");
      w.write("server:\n  applicationConnectors:\n    - type: http\n      port: 0\n"
        + "  adminConnectors:\n    - type: http\n      port: 0\n");
    }
    assertNotNull(f);
    return f;
  }
}
