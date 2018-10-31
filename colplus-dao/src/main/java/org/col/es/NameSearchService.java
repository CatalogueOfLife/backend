package org.col.es;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameUsageWrapper;
import org.col.es.query.EsSearchRequest;
import org.col.es.translate.NameSearchRequestTranslator;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;
import static org.col.es.EsConfig.NAME_USAGE_BASE;

public class NameSearchService {

  private static final Logger LOG = LoggerFactory.getLogger(NameSearchService.class);

  private static String REQUEST_URL = getUrl();

  private final RestClient client;
  private final EsConfig cfg;

  public NameSearchService(RestClient client, EsConfig cfg) {
    this.client = client;
    this.cfg = cfg;
  }

  public ResultPage<NameUsageWrapper<?>> search(NameSearchRequest query, Page page)
      throws InvalidQueryException {
    NameSearchRequestTranslator translator = new NameSearchRequestTranslator(query, page);
    EsSearchRequest esQuery = translator.translate();
    if (LOG.isDebugEnabled()) {
      LOG.debug(printQuery(esQuery, true));
    }
    Request request = new Request("GET", REQUEST_URL);
    request.setJsonEntity(printQuery(esQuery, false));
    Response response;
    try {
      response = client.performRequest(request);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new RuntimeException(response.getStatusLine().getReasonPhrase());
    }
    //response.
    return null;
  }

  private String printQuery(EsSearchRequest esQuery, boolean pretty) {
    try {
      if (pretty) {
        return cfg.nameUsage.getQueryWriter().withDefaultPrettyPrinter().writeValueAsString(esQuery);
      }
      return cfg.nameUsage.getQueryWriter().writeValueAsString(esQuery);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getUrl() {
    return String.format("/%s/%s", NAME_USAGE_BASE, DEFAULT_TYPE_NAME);
  }
}
