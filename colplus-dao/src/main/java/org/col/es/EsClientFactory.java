package org.col.es;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class EsClientFactory {

  private final EsConfig cfg;

  public EsClientFactory(EsConfig cfg) {
    this.cfg = cfg;
  }

  public RestClient createClient() {
    String[] hosts = cfg.hosts.split(",");
    String[] ports = cfg.ports.split(",");
    HttpHost[] hhtpHosts = new HttpHost[hosts.length];
    for (int i = 0; i < hosts.length; i++) {
      int port;
      if (ports == null || ports.length == i) {
        port = 9200;
      } else {
        port = Integer.parseInt(ports[i]);
      }
      hhtpHosts[i] = new HttpHost(hosts[i], port);
    }
    return RestClient.builder(hhtpHosts).build();
  }

}
