package org.col.es;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

public class EsClientFactory {

  private final EsConfig cfg;

  public EsClientFactory(EsConfig cfg) {
    this.cfg = cfg;
  }

  public Client getEsClient() {
    Builder builder = Settings.builder();
    builder.put("cluster.name", cfg.cluster);
    builder.put("client.transport.ping_timeout", "20s");
    Settings settings = builder.build();
    TransportClient client = new PreBuiltTransportClient(settings);
    InetAddress host;
    try {
      host = InetAddress.getByName(cfg.host);
    } catch (UnknownHostException e) {
      throw new EsException(e);
    }
    InetSocketTransportAddress addr = new InetSocketTransportAddress(host, cfg.port);
    client.addTransportAddress(addr);
    return client;
  }

}
