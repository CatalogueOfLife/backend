package org.col.dw.health;


import com.codahale.metrics.health.HealthCheck;
import org.col.es.EsConfig;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

/**
 * Contacts elastic search REST API to see if we are alive.
 */
public class EsHealthCheck extends HealthCheck {
  
  final RestClient client;
  final EsConfig cfg;
  
  public EsHealthCheck(RestClient client, EsConfig cfg) {
    this.client = client;
    this.cfg = cfg;
  }
  
  
  @Override
  protected Result check() {
    try {
      String idxName = cfg.indexName(ES_INDEX_NAME_USAGE);
      Request req = new Request("HEAD", idxName);
      Response resp = client.performRequest(req);
  
      if (resp.getStatusLine().getStatusCode() == 200) {
        return Result.healthy("ES index %s exists on %s", idxName, cfg.hosts);
      }
      return Result.unhealthy("Cannot contact ES index %s on %s", idxName, cfg.hosts);
      
    } catch (Exception e) {
      return Result.unhealthy(e);
    }
  }
}