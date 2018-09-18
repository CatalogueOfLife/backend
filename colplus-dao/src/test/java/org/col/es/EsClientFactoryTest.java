package org.col.es;

import java.io.IOException;

import org.elasticsearch.client.RestClient;
import org.junit.Test;

public class EsClientFactoryTest extends EsReadTestBase {

  @Test
  public void getClient() throws IOException {
    try (RestClient c = getEsClient()) {
      // ... will we get a connection?
    }
  }

}
