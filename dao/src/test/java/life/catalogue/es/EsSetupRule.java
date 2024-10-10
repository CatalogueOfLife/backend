package life.catalogue.es;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.common.util.YamlUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import life.catalogue.db.PgConfig;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Spins up an elasticsearch test container.
 * To be used as a ClassRule.
 */
public class EsSetupRule extends ExternalResource {

  public static String VERSION = "8.1.3";

  /**
   * Dataset key used by default for tests.
   */
  public static final int DATASET_KEY = TestEntityGenerator.DATASET11.getKey();

  private static final Logger LOG = LoggerFactory.getLogger(EsSetupRule.class);

  private static ElasticsearchContainer CONTAINER;
  private static String PASSWORD = "ase213HUithbnjk";

  private EsConfig cfg;
  private RestClient client;

  @Override
  protected void before() throws Throwable {
    super.before();
    CONTAINER = setupElastic();
    CONTAINER.start();
    cfg = buildContainerConfig(CONTAINER);
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
    cfg.user = "elastic";
    cfg.password = PASSWORD;
    cfg.nameUsage = new IndexConfig();
    cfg.nameUsage.name = "test_name_usage";
    System.out.println("Postgres container using port " + cfg.ports);
    return cfg;
  }

  /**
   * Returns an Elasticsearch REST client. Do NOT call using try-with-resources block. The client will be torn down by EsSetupRule.
   * 
   * @return
   */
  public RestClient getClient() {
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
        client.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      client = null;
    }
    CONTAINER.stop();
  }

}
