package org.col.es;

import java.util.List;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class EsConfig {

  public String host;
  public int port;

  public String cluster;
  public List<IndexConfig> indices;

  public RestClient connect() {
    return RestClient.builder(new HttpHost(host, port, "http"))
        .setMaxRetryTimeoutMillis(10000)
        .build();
  }
}
