package life.catalogue.es;

import life.catalogue.config.EsConfig;
import life.catalogue.es.json.EsModule;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;

public class EsClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(EsClientFactory.class);

  private final EsConfig cfg;

  public EsClientFactory(EsConfig cfg) {
    this.cfg = Preconditions.checkNotNull(cfg, "ES config required");
  }

  /**
   * Creates an ElasticsearchClient using the ES 9.x Rest5Client builder API.
   */
  public ElasticsearchClient createClient() {
    String[] hosts = cfg.hosts.split(",");
    String scheme = cfg.ssl ? "https" : "http";

    List<URI> uris = new ArrayList<>();
    for (int i = 0; i < hosts.length; i++) {
      uris.add(URI.create(scheme + "://" + hosts[i].trim()));
    }

    LOG.info("Connecting to Elasticsearch using scheme={} hosts={}", scheme, cfg.hosts);

    Rest5ClientBuilder builder = Rest5Client.builder(uris.toArray(URI[]::new))
      .setCompressionEnabled(true)
      .setConnectionConfigCallback(b -> b.setConnectTimeout(Timeout.ofMilliseconds(cfg.connectTimeout)))
      .setRequestConfigCallback(b -> b.setResponseTimeout(Timeout.ofMilliseconds(cfg.socketTimeout)));

    if (cfg.ssl) {
      SSLContext sslContext = buildTrustAllSslContext();
      builder.setSSLContext(sslContext)
             .setConnectionManagerCallback(mgr -> mgr
               .setMaxConnPerRoute(cfg.maxConnPerRoute)
               .setMaxConnTotal(cfg.maxConnTotal)
               .setTlsStrategy(ClientTlsStrategyBuilder.create()
                 .setSslContext(sslContext)
                 .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                 .build()));
    } else {
      builder.setConnectionManagerCallback(mgr -> mgr
        .setMaxConnPerRoute(cfg.maxConnPerRoute)
        .setMaxConnTotal(cfg.maxConnTotal));
    }

    if (cfg.user != null) {
      LOG.info("Adding authentication for user {} to Elasticsearch client", cfg.user);
      String credentials = Base64.getEncoder().encodeToString(
        (cfg.user + ":" + cfg.password).getBytes(StandardCharsets.UTF_8));
      builder.setDefaultHeaders(new BasicHeader[]{new BasicHeader("Authorization", "Basic " + credentials)});
    }

    if (cfg.pathPrefix != null) {
      builder.setPathPrefix(cfg.pathPrefix);
    }

    var mapper = new JacksonJsonpMapper(EsModule.contentMapper());
    return new ElasticsearchClient(new Rest5ClientTransport(builder.build(), mapper));
  }

  private static SSLContext buildTrustAllSslContext() {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[]{new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
      }}, new SecureRandom());
      return ctx;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create trust-all SSL context", e);
    }
  }

}
