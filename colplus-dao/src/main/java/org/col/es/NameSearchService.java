package org.col.es;

import java.io.IOException;

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

  public NameSearchService(RestClient client) {
    this.client = client;
  }

  public ResultPage<NameUsageWrapper<?>> search(NameSearchRequest query, Page page)
      throws InvalidQueryException {
    NameSearchRequestTranslator translator = new NameSearchRequestTranslator(query, page);
    EsSearchRequest esQuery = translator.translate();
    if(LOG.isDebugEnabled()) {
      //LOG.debug(msg);
    }
    Request request = new Request("GET", REQUEST_URL);
    request.setJsonEntity(esQuery.toString());
    Response response;
    try {
      response = client.performRequest(request);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new RuntimeException(response.getStatusLine().getReasonPhrase());
    }
    
    return null;
  }

  private static String getUrl() {
    return String.format("/%s/%s", NAME_USAGE_BASE, DEFAULT_TYPE_NAME);
  }
}
