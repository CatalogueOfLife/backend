package life.catalogue.dw.health;


import life.catalogue.api.vocab.Datasets;
import life.catalogue.es.EsConfig;
import life.catalogue.es.EsModule;
import life.catalogue.es.EsUtil;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.TermQuery;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Contacts elastic search REST API to see if we are alive.
 */
public class EsHealthCheck extends HealthCheck {
  
  final RestClient client;
  final EsConfig cfg;
  final Request req;

  public EsHealthCheck(RestClient client, EsConfig cfg) {
    this.client = client;
    this.cfg = cfg;
    Query q = new TermQuery("datasetKey", Datasets.COL);
    EsSearchRequest esSearchRequest = new EsSearchRequest().where(q).size(1);
    String endpoint = String.format("/%s/_search", cfg.nameUsage.name);
    req = new Request("GET", endpoint);
    try {
      req.setJsonEntity(EsModule.write(esSearchRequest));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to prepare ES health search request");
    }
  }

  @Override
  protected Result check() throws Exception {
    String idxName = cfg.nameUsage.name;
    if (!EsUtil.indexExists(client, idxName)) {
      return Result.unhealthy("Cannot contact ES index %s on %s", idxName, cfg.hosts);
    }
    Response resp = EsUtil.executeRequest(client, req);
    if (resp.getStatusLine().getStatusCode() != 200) {
      return Result.unhealthy("Cannot search COL in ES index %s on %s. HTTP %s: %s",
        idxName, cfg.hosts, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase());
    }
    return Result.healthy("ES index %s exists on %s and can be searched for COL", idxName, cfg.hosts);
  }
}