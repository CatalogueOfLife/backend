package org.col.es.name.search;

import java.io.IOException;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Page;
import org.col.api.search.NameUsageSearchRequest;
import org.col.api.search.NameUsageSearchResponse;
import org.col.es.EsException;
import org.col.es.name.NameUsageQueryService;
import org.col.es.name.NameUsageEsResponse;
import org.col.es.query.EsSearchRequest;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.api.search.NameUsageSearchParameter.DATASET_KEY;
import static org.col.api.search.NameUsageSearchParameter.USAGE_ID;

public class NameUsageSearchServiceEs extends NameUsageQueryService implements NameUsageSearchService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);

  public NameUsageSearchServiceEs(String indexName, RestClient client) {
    super(indexName, client);
  }

  /**
   * Converts the Elasticsearh response coming back from the query into an API object (NameSearchResponse).
   * 
   * @param request
   * @param page
   * @return
   */
  public NameUsageSearchResponse search(NameUsageSearchRequest request, Page page) {
    try {
      return search(index, request, page);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @VisibleForTesting
  public NameUsageSearchResponse search(String index, NameUsageSearchRequest request, Page page) throws IOException {
    validateRequest(request);
    RequestTranslator translator = new RequestTranslator(request, page);
    EsSearchRequest esSearchRequest = translator.translate();
    NameUsageSearchResponse response = search(index, esSearchRequest, page);
    if (mustHighlight(request, response)) {
      NameSearchHighlighter highlighter = new NameSearchHighlighter(request, response);
      highlighter.highlightNameUsages();
    }
    return response;
  }

  @VisibleForTesting
  public NameUsageSearchResponse search(String index, EsSearchRequest esSearchRequest, Page page) throws IOException {
    NameUsageEsResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameSearchResultConverter converter = new NameSearchResultConverter(esResponse);
    return converter.transferResponse(page);
  }

  private static void validateRequest(NameUsageSearchRequest request) {
    NameUsageSearchRequest copy = request.copy();
    if (copy.hasFilter(USAGE_ID)) {
      if (copy.getFilterValues(USAGE_ID).size() > 1) {
        throw new EsException("Bad search request: only one usage id allowed");
      }
      if (!copy.hasFilter(DATASET_KEY)) {
        throw new EsException("Bad search request: dataset key required when specifying usage id");
      }
      copy.removeFilter(DATASET_KEY);
      copy.removeFilter(USAGE_ID);
      if (!copy.getFilters().isEmpty()) {
        throw new EsException("Bad search request: no filters besides dataset key allowed when specifying usage id");
      }
    }
    // More validations ...
  }

  private static boolean mustHighlight(NameUsageSearchRequest request, NameUsageSearchResponse res) {
    return request.isHighlight()
        && !res.getResult().isEmpty()
        && !StringUtils.isEmpty(request.getQ())
        && !request.hasFilter(USAGE_ID);
  }
}
