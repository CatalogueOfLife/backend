package life.catalogue.es2;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import life.catalogue.config.IndexConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import java.io.IOException;

public class EsTestBase {
  @ClassRule
  //public static EsConnectRule esSetup = new EsConnectRule();
  public static EsSetupRule esSetup = new EsSetupRule();

  protected ElasticsearchClient client;
  protected IndexConfig cfg;

  @Before
  public void setUp() throws IOException {
    client = esSetup.getClient();
    cfg = esSetup.getEsConfig().index;
    EsUtil.createIndex(client, cfg);
  }

  @After
  public void tearDown() throws IOException {
    EsUtil.deleteIndex(client, cfg);
  }

}
