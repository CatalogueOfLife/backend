package org.col.es;

import org.junit.Test;
import org.elasticsearch.client.Client;

public class EsClientFactoryTest extends EsReadTestBase {

  @Test
  public void getClient() {
    try (Client c = getEsClient()) {
      // ... will we get a connection?
    }
  }

}
