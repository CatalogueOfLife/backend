package org.col.es;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.col.common.util.YamlUtils;
import org.elasticsearch.client.RestClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.allegro.tech.embeddedelasticsearch.EmbeddedElastic;

public class EsSetupRule extends ExternalResource {

  private static final Logger LOG = LoggerFactory.getLogger(EsSetupRule.class);
  private static final String ES_VERSION = "6.4.1";

  private EmbeddedElastic ee;

  private EsConfig cfg;

  @Override
  protected void before() throws Throwable {
    super.before();
    cfg = YamlUtils.read(EsConfig.class, "/es-test.yaml");
    if (cfg.embedded()) {
      LOG.info("Starting embedded Elasticsearch");
      try {
        ee = EmbeddedElastic.builder().withInstallationDirectory(new File(cfg.hosts))
            .withElasticVersion(ES_VERSION).withStartTimeout(15, TimeUnit.SECONDS).build().start();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      LOG.info("Using external Elasticsearch server");
    }
  }

  public RestClient getEsClient() {
    return new EsClientFactory(cfg).createClient();
  }

  public EsConfig getEsConfig() {
    return cfg;
  }

  @Override
  protected void after() {
    super.after();
    if (ee != null) {
      ee.stop();
    }
  }

}
