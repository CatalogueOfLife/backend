package org.col.es;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class EsClientFactory {

  private final EsConfig cfg;

  public EsClientFactory(EsConfig cfg) {
    this.cfg = cfg;
  }

  public RestClient createClient() {
    if (cfg == null || cfg.embedded()) {
      return RestClient.builder(new HttpHost("127.0.0.1", 9200)).build();
    }
    String[] hosts = cfg.hosts.split(",");
    String[] ports = cfg.ports == null ? new String[] {"9200"} : cfg.ports.split(",");
    HttpHost[] hhtpHosts = new HttpHost[hosts.length];
    for (int i = 0; i < hosts.length; i++) {
      int port = Integer.parseInt(ports[i]);
      hhtpHosts[i] = new HttpHost(hosts[i], port);
    }
    return RestClient.builder(hhtpHosts).build();
  }

}
