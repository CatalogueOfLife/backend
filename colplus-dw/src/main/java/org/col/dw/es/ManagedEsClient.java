package org.col.dw.es;

import org.elasticsearch.client.RestClient;

import io.dropwizard.lifecycle.Managed;

public class ManagedEsClient implements Managed {

  private final RestClient client;

  public ManagedEsClient(RestClient client) {
    this.client = client;
  }

  @Override
  public void start() throws Exception {}

  @Override
  public void stop() throws Exception {
    client.close();
  }

}
