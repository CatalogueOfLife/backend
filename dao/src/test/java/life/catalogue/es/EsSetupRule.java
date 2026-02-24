package life.catalogue.es;

import life.catalogue.config.EsConfig;
import life.catalogue.config.IndexConfig;

import java.time.LocalDate;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * Spins up an elasticsearch test container.
 * To be used as a ClassRule.
 */
public class EsSetupRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(EsSetupRule.class);
  private static String VERSION = "9.3.0";

  private static ElasticsearchContainer CONTAINER;
  protected static String PASSWORD = "ase213HUithbnjk";
  protected static String USER = "elastic";

  private EsConfig cfg;
  private ElasticsearchClient client;

  @Override
  protected void before() throws Throwable {
    super.before();
    CONTAINER = setupElastic();
    CONTAINER.start();
    cfg = buildContainerConfig(CONTAINER);
    System.out.println("ES container using index " + cfg.index.name + " on port " + cfg.ports);

    client = new EsClientFactory(cfg).createClient();
    LOG.info("Using Elasticsearch on {}:{}", cfg.hosts, cfg.ports);
  }

  private ElasticsearchContainer setupElastic() {
    return new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:" + VERSION)
      // disable SSL
      .withEnv("xpack.security.transport.ssl.enabled", "false")
      .withEnv("xpack.security.http.ssl.enabled", "false")
      .withPassword(PASSWORD);
  }

  public EsConfig buildContainerConfig(ElasticsearchContainer container) {
    EsConfig cfg = new EsConfig();
    cfg.hosts = container.getHost();
    cfg.ports = container.getFirstMappedPort().toString();
    cfg.user = USER;
    cfg.password = PASSWORD;
    cfg.index = new IndexConfig();
    cfg.index.name = LocalDate.now() + "-" + System.currentTimeMillis();
    System.out.println("ES container using port " + cfg.ports);
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
    CONTAINER.stop();
  }

}
