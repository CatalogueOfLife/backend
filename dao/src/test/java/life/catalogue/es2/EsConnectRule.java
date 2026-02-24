package life.catalogue.es2;

import life.catalogue.config.EsConfig;
import life.catalogue.config.IndexConfig;

import java.time.LocalDate;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import static life.catalogue.es2.EsSetupRule.PASSWORD;
import static life.catalogue.es2.EsSetupRule.USER;

/**
 * Connects to an external running ES container.
 * To be used as a ClassRule for quick tests.
 */
public class EsConnectRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(EsConnectRule.class);
  private EsConfig cfg;
  private ElasticsearchClient client;

  @Override
  protected void before() throws Throwable {
    super.before();
    cfg = buildContainerConfig();
    client = new EsClientFactory(cfg).createClient();
    LOG.info("Using Elasticsearch on {}:{}", cfg.hosts, cfg.ports);
  }

  public EsConfig buildContainerConfig() {
    EsConfig cfg = new EsConfig();
    cfg.user = USER;
    cfg.password = PASSWORD;
    cfg.ssl = true;
    cfg.index = new IndexConfig();
    cfg.index.name = LocalDate.now() + "-" + System.currentTimeMillis();
    System.out.println("ES container using index " + cfg.index.name + " on port " + cfg.ports);
    return cfg;
  }

  /**
   * Returns the Elasticsearch client. Do NOT call close on this client. It will be torn down by EsSetupRule.
   */
  public ElasticsearchClient getClient() {
    return client;
  }

  public EsConfig getEsConfig() {
    return cfg;
  }

  @Override
  protected void after() {
    super.after();
    if (client != null) {
      try {
        EsUtil.close(client);
      } catch (java.io.IOException e) {
        throw new RuntimeException(e);
      }
      client = null;
    }
  }

}
