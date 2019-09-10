package org.col.es.name.suggest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.api.search.NameSuggestion;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.name.NameUsageResponse;
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

  public NameSuggestResponse suggestNames(NameSuggestRequest request) throws IOException {
    RequestTranslator translator = new RequestTranslator(request);
    EsSearchRequest query = translator.translate();
    NameUsageResponse esResponse = executeSearchRequest(index, query);
    List<NameSuggestion> suggestions = new ArrayList<>(request.getLimit());
    SuggestionFactory factory = new SuggestionFactory(request);
    esResponse.getHits().getHits().forEach(hit -> {
      /*
       * Theoretically the user could have typed something that matched both a scientific name and a vernacular name in one
       * and the same document:
       */
      if (hit.matchedQuery(QTranslator.SN_QUERY_NAME)) {
        suggestions.add(factory.createSuggestion(hit, false));
      }
      if (hit.matchedQuery(QTranslator.VN_QUERY_NAME)) {
        suggestions.add(factory.createSuggestion(hit, true));
      }
    });
    return new NameSuggestResponse(suggestions);
  }

}
