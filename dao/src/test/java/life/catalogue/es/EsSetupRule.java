package life.catalogue.es;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.common.util.YamlUtils;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.elasticsearch.client.RestClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

/**
 * To be used as a ClassRule. Mainly installs/configures an external Elasticsearch instance.
 */
public class EsSetupRule extends ExternalResource {

  // required version of elastic to work against - will be verified
  private static final int[] ES_VERSION = new int[] {8};

  /**
   * Dataset key used by default for tests.
   */
  public static final int DATASET_KEY = TestEntityGenerator.DATASET11.getKey();

  private static final Logger LOG = LoggerFactory.getLogger(EsSetupRule.class);

  private final int[] esVersion;

  private EsConfig cfg;
  private RestClient client;

  public EsSetupRule() {
    String esVersion = System.getenv("REQUIRED_ES_VERSION");
    if (esVersion == null) {
      esVersion = System.getProperty("REQUIRED_ES_VERSION");
      if (esVersion == null) {
        esVersion = Joiner.on(".").join(ArrayUtils.toObject(ES_VERSION));
      }
    }
    this.esVersion = Arrays.stream(esVersion.split("\\.")).mapToInt(Integer::parseInt).toArray();
  }

  @Override
  protected void before() throws Throwable {
    super.before();
    cfg = YamlUtils.read(EsConfig.class, "/es-test.yaml");
    LOG.info("Connecting to Elasticsearch on {}:{}", cfg.hosts, cfg.ports);
    // connect and verify version
    client = new EsClientFactory(cfg).createClient();
    EsServerVersion v = EsServerVersion.getInstance(client);
    if (!v.is(esVersion)) {
      throw new IllegalStateException("Elasticsearch is running the wrong version " + v.getVersionString() +
          ". Expecting " + Joiner.on(".").join(ArrayUtils.toObject(esVersion)));
    }
    LOG.info("Using Elasticsearch {} on {}:{}", v.getVersionString(), cfg.hosts, cfg.ports);
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
  }

}
