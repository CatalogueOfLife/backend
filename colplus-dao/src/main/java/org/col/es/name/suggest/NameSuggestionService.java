package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.es.name.search.NameUsageSearchService;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameSuggestionService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  private final String index;
  private final RestClient client;

  public NameSuggestionService(String indexName, RestClient client) {
    this.index = indexName;
    this.client = client;
  }

  public NameSuggestResponse suggestNames(NameSuggestRequest request) {
    return null;
  }

}
