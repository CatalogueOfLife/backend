package life.catalogue.es;

import life.catalogue.concurrent.NamedThreadFactory;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class EsClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(EsClientFactory.class);

  private final EsConfig cfg;

  public EsClientFactory(EsConfig cfg) {
    this.cfg = Preconditions.checkNotNull(cfg, "ES config required");
  }

  public RestClient createClient() {
    return createClientBuilder().build();
  }

  public RestClientBuilder createClientBuilder() {
    String[] hosts = cfg.hosts == null ? new String[]{"localhost"} : cfg.hosts.split(",");
    String[] ports = cfg.ports == null ? new String[]{"9200"} : cfg.ports.split(",");
    HttpHost[] httpHosts = new HttpHost[hosts.length];
    for(int i = 0; i < hosts.length; i++) {
      int port = Integer.parseInt(ports[i]);
      httpHosts[i] = new HttpHost(hosts[i], port);
    }
    var builder = RestClient.builder(httpHosts)
        .setCompressionEnabled(true)
        .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
          @Override
          public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
            return httpClientBuilder.setThreadFactory(new NamedThreadFactory("es-client"));
          }
        })
        .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
          @Override
          public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
            return requestConfigBuilder
                .setConnectTimeout(cfg.connectTimeout)
                .setSocketTimeout(cfg.socketTimeout);
          }
        });

    LOG.info("Connecting to Elasticsearch using hosts={}; ports={}", cfg.hosts, (cfg.ports == null ? "9200" : cfg.ports));
    if (cfg.user != null) {
      final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY,
        new UsernamePasswordCredentials(cfg.user, cfg.password)
      );
      // add authentication
      builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
      LOG.info("Adding authentication for user {} to Elasticsearch client", cfg.user);
    }
    return builder;
  }

}
