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

  private int shards;
  private EsConfig cfg;
  private ElasticsearchClient client;

  public EsSetupRule() {
    this(1);
  }
  public EsSetupRule(int shards) {
    this.shards = shards;
  }

  @Override
  protected void before() throws Throwable {
    super.before();
    CONTAINER = setupElastic();
    CONTAINER.start();
    cfg = buildContainerConfig(CONTAINER);
    System.out.println("ES container using index " + cfg.index.name + " on host " + cfg.hosts);

    client = new EsClientFactory(cfg).createClient();
    LOG.info("Using Elasticsearch on {}", cfg.hosts);
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
    cfg.hosts = container.getHost() + ":" + container.getFirstMappedPort().toString();
    cfg.user = USER;
    cfg.password = PASSWORD;
    cfg.index = new IndexConfig();
    cfg.index.name = LocalDate.now() + "-" + System.currentTimeMillis();
    cfg.index.numShards = shards;
    System.out.println("ES container using hosts " + cfg.hosts);
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
