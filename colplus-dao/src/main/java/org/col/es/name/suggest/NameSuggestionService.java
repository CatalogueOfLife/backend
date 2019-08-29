package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.name.NameUsageService;
import org.col.es.name.search.NameUsageSearchService;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameSuggestionService extends NameUsageService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  public NameSuggestionService(String indexName, RestClient client) {
    super(indexName, client);
  }

  public NameSuggestResponse suggestNames(NameSuggestRequest request) {
    NameSuggestRequestTranslator translator = new NameSuggestRequestTranslator(request);
    EsSearchRequest esSearchRequest = translator.translate();
    return null;
  }

}
