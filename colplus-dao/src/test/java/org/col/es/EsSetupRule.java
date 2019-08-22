package org.col.es;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Strings;

import org.col.api.TestEntityGenerator;
import org.col.common.io.PortUtil;
import org.col.common.util.YamlUtils;
import org.col.es.EsClientFactory;
import org.col.es.EsConfig;
import org.elasticsearch.client.RestClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.allegro.tech.embeddedelasticsearch.EmbeddedElastic;
import pl.allegro.tech.embeddedelasticsearch.PopularProperties;

/**
 * To be used as a ClassRule. Mainly installs/configures embedded Elasticsearch.
 */
public class EsSetupRule extends ExternalResource {

  /**
   * Name of the index used by default for tests.
   */
  public static final String TEST_INDEX = "name_usage_test";
  /**
   * Dataset key used by default for tests.
   */
  public static final int DATASET_KEY = TestEntityGenerator.DATASET11.getKey();

  private static final Logger LOG = LoggerFactory.getLogger(EsSetupRule.class);
  private static final String ES_VERSION = "6.4.1";
  
  private EsConfig cfg;
  private EmbeddedElastic ee;
  private RestClient esClient;

  @Override
  protected void before() throws Throwable {
    super.before();
    cfg = YamlUtils.read(EsConfig.class, "/es-test.yaml");
    if (cfg.embedded()) {
      LOG.info("Starting embedded Elasticsearch");
      cfg.hosts = "127.0.0.1";
      try {
        // use configured port or assign free ports using local socket 0
        int httpPort;
        if (Strings.isNullOrEmpty(cfg.ports)) {
          httpPort = PortUtil.findFreePort();
          cfg.ports = String.valueOf(httpPort);
        } else {
          httpPort = Integer.parseInt(cfg.ports);
        }
        int tcpPort = PortUtil.findFreePort();
        ee = EmbeddedElastic.builder()
            .withInstallationDirectory(new File(cfg.hosts))
            .withElasticVersion(ES_VERSION)
            .withStartTimeout(60, TimeUnit.SECONDS)
            .withSetting(PopularProperties.TRANSPORT_TCP_PORT, tcpPort)
            .withSetting(PopularProperties.HTTP_PORT, httpPort)
            .build()
            .start();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      LOG.info("Using external Elasticsearch server");
    }
  }

  /**
   * Returns an Elasticsearch REST client. Do NOT call using try-with-resources block. The client will be torn down by EsSetupRule.
   * 
   * @return
   */
  public RestClient getEsClient() {
    if (esClient == null) {
      esClient = new EsClientFactory(cfg).createClient();
    }
    return esClient;
  }

  public EsConfig getEsConfig() {
    return cfg;
  }

  @Override
  protected void after() {
    super.after();
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      esClient = null;
    }
    if (ee != null) {
      ee.stop();
      ee = null;
    }
  }

}
