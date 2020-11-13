package life.catalogue.es;

import org.junit.Test;

public class EsServerVersionTest {

  @Test(expected = EsRequestException.class)
  public void notRunning() throws Exception {
    EsConfig cfg = new EsConfig();
    cfg.hosts = "fantasy.ion";
    System.out.println("Connecting to Elasticsearch on " + cfg.hosts +":"+ cfg.ports);
    var client = new EsClientFactory(cfg).createClient();
    EsServerVersion.getInstance(client);
  }

}