package life.catalogue.dw.health;


import life.catalogue.config.EsConfig;
import life.catalogue.es2.EsUtil;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import com.codahale.metrics.health.HealthCheck;

/**
 * Contacts elastic search REST API to see if we are alive.
 */
public class EsHealthCheck extends HealthCheck {

  final ElasticsearchClient client;
  final EsConfig cfg;

  public EsHealthCheck(ElasticsearchClient client, EsConfig cfg) {
    this.client = client;
    this.cfg = cfg;
  }

  @Override
  protected Result check() throws Exception {
    String idxName = cfg.index.name;
    if (!EsUtil.indexExists(client, idxName)) {
      return Result.unhealthy("Cannot contact ES index %s on %s", idxName, cfg.hosts);
    }
    int cnt = EsUtil.count(client, idxName);
    if (cnt < 0) {
      return Result.unhealthy("Cannot search ES index %s on %s", idxName, cfg.hosts);
    }
    return Result.healthy("ES index %s exists on %s with %s documents", idxName, cfg.hosts, cnt);
  }
}
