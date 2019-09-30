package org.col.es;

import com.google.common.base.Preconditions;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(EsClientFactory.class);

  private final EsConfig cfg;

  public EsClientFactory(EsConfig cfg) {
    this.cfg = Preconditions.checkNotNull(cfg, "ES config required");
  }

  public RestClient createClient() {
    String[] hosts = cfg.hosts == null ? new String[]{"localhost"} : cfg.hosts.split(",");
    String[] ports = cfg.ports == null ? new String[]{"9200"} : cfg.ports.split(",");
    HttpHost[] httpHosts = new HttpHost[hosts.length];
    for(int i = 0; i < hosts.length; i++) {
      int port = Integer.parseInt(ports[i]);
      httpHosts[i] = new HttpHost(hosts[i], port);
    }
    LOG.info("Connecting to Elasticsearch using hosts={}; ports={}", cfg.hosts, (cfg.ports == null ? "9200" : cfg.ports));
    return RestClient.builder(httpHosts)
        .setRequestConfigCallback(
            new RestClientBuilder.RequestConfigCallback() {
              @Override
              public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
                return requestConfigBuilder
                    .setConnectTimeout(10000)
                    .setSocketTimeout(120000);
              }
            }).build();
  }

}
