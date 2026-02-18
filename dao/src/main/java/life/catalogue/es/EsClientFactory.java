package life.catalogue.es;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;

import com.google.common.base.Preconditions;

public class EsClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(EsClientFactory.class);

  private final EsConfig cfg;

  public EsClientFactory(EsConfig cfg) {
    this.cfg = Preconditions.checkNotNull(cfg, "ES config required");
  }

  /**
   * Creates an ElasticsearchClient using the ES 9.x builder API.
   */
  public ElasticsearchClient createClient() {
    String[] hosts = cfg.hosts == null ? new String[]{"localhost"} : cfg.hosts.split(",");
    String[] ports = cfg.ports == null ? new String[]{"9200"} : cfg.ports.split(",");

    List<URI> uris = new ArrayList<>();
    for (int i = 0; i < hosts.length; i++) {
      String port = ports.length > i ? ports[i] : ports[0];
      uris.add(URI.create("http://" + hosts[i].trim() + ":" + port.trim()));
    }

    LOG.info("Connecting to Elasticsearch using hosts={}; ports={}", cfg.hosts, (cfg.ports == null ? "9200" : cfg.ports));

    return ElasticsearchClient.of(b -> {
      b.hosts(uris)
       .useCompression(true)
       .jsonMapper(new JacksonJsonpMapper(EsModule.contentMapper()));
      if (cfg.user != null) {
        b.usernameAndPassword(cfg.user, cfg.password);
        LOG.info("Adding authentication for user {} to Elasticsearch client", cfg.user);
      }
      return b;
    });
  }

}
