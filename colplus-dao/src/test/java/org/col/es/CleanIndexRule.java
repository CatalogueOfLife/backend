package org.col.es;

import java.io.IOException;

import org.col.common.util.YamlUtils;
import org.elasticsearch.client.RestClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To be used as regular Rule. Drops/recreates the Elasticsearch index
 */
public class CleanIndexRule extends ExternalResource {

  private static final Logger LOG = LoggerFactory.getLogger(CleanIndexRule.class);

  private final EsConfig esConfig;
  private final RestClient esClient;

  public CleanIndexRule(RestClient esClient) {
    try {
      this.esConfig = YamlUtils.read(EsConfig.class, "/es-test.yaml");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.esClient = esClient;
  }

  @Override
  public void before() throws Throwable {
    super.before();
    try {
      LOG.debug("Dumping/creating test index \"{}\"", EsSetupRule.TEST_INDEX);
      EsUtil.deleteIndex(esClient, EsSetupRule.TEST_INDEX);
      EsUtil.createIndex(esClient, EsSetupRule.TEST_INDEX, esConfig.nameUsage);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void after() {
    super.after();
  }

}
