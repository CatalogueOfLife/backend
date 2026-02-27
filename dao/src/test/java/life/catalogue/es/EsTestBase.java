package life.catalogue.es;

import life.catalogue.config.EsConfig;
import life.catalogue.config.IndexConfig;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

public class EsTestBase {

  @ClassRule
  //public static EsConnectRule esSetup = new EsConnectRule();
  public static EsSetupRule esSetup = new EsSetupRule();

  protected ElasticsearchClient client;
  protected EsConfig cfg;
  protected String indexName;

  @Before
  public void setUp() throws IOException {
    client = esSetup.getClient();
    cfg = esSetup.getEsConfig();
    indexName = cfg.index.name;
    EsUtil.createIndex(client, cfg.index);
  }

  @After
  public void tearDown() throws IOException {
    EsUtil.deleteIndex(client, cfg.index);
  }

}
