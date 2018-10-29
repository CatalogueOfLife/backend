package org.col.es;

import java.io.IOException;

import com.google.common.annotations.VisibleForTesting;

import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.EsSearchRequest;
import org.col.es.translate.NameSearchRequestTranslator;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

public class NameSearchService {

  private final RestClient client;
  private final EsConfig esConfig;

  public NameSearchService(RestClient client, EsConfig esConfig) {
    this.client = client;
    this.esConfig = esConfig;
  }

  public ResultPage<NameUsage> search(NameSearchRequest query, Page page)
      throws InvalidQueryException {
    String index = EsConfig.NAME_USAGE_BASE;
    return search(index, query, page);
  }

  @VisibleForTesting
  ResultPage<NameUsage> search(String index, NameSearchRequest query, Page page)
      throws InvalidQueryException {
    Request request = new Request("GET", "/" + index + "/_doc");
    EsSearchRequest esr = new NameSearchRequestTranslator(query, page).translate();
    request.setJsonEntity(esr.toString());
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
}
