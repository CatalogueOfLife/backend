package org.col.es.name.search;

import java.io.IOException;

import com.google.common.annotations.VisibleForTesting;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchResponse;
import org.col.es.EsException;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.name.NameUsageResponse;
import org.col.es.name.NameUsageService;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameUsageSearchService extends NameUsageService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  public NameUsageSearchService(String indexName, RestClient client) {
    super(indexName, client);
  }

  /**
   * Converts the Elasticsearh response coming back from the query into an API object (NameSearchResponse).
   * 
   * @param request
   * @param page
   * @return
   */
  public NameSearchResponse search(NameSearchRequest request, Page page) {
    try {
      return search(index, request, page);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @VisibleForTesting
  public NameSearchResponse search(String index, NameSearchRequest request, Page page) throws IOException {
    RequestTranslator translator = new RequestTranslator(request, page);
    EsSearchRequest esSearchRequest = translator.translate();
    NameUsageResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameSearchResultConverter transfer = new NameSearchResultConverter(esResponse);
    NameSearchResponse response = transfer.transferResponse(page);
    if (mustHighlight(request, response)) {
      NameSearchHighlighter highlighter = new NameSearchHighlighter(request, response);
      highlighter.highlightNameUsages();
    }
    return response;
  }

  @VisibleForTesting
  public NameSearchResponse search(String index, EsSearchRequest esSearchRequest, Page page) throws IOException {
    NameUsageResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameSearchResultConverter transfer = new NameSearchResultConverter(esResponse);
    return transfer.transferResponse(page);
  }

  private static boolean mustHighlight(NameSearchRequest req, NameSearchResponse res) {
    return req.isHighlight() && !res.getResult().isEmpty() && req.getQ() != null && req.getQ().length() > 1;
  }
}
