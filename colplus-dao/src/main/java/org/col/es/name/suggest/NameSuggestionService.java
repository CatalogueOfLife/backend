package org.col.es.name.suggest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.api.search.NameSuggestion;
import org.col.es.EsException;
import org.col.es.name.NameUsageQueryService;
import org.col.es.name.NameUsageResponse;
import org.col.es.name.search.NameUsageSearchServiceEs;
import org.col.es.query.EsSearchRequest;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameSuggestionService extends NameUsageQueryService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);

  public NameSuggestionService(String indexName, RestClient client) {
    super(indexName, client);
  }

  public NameSuggestResponse suggest(NameSuggestRequest request) {
    RequestTranslator translator = new RequestTranslator(request);
    EsSearchRequest query = translator.translate();
    NameUsageResponse esResponse;
    try {
      esResponse = executeSearchRequest(index, query);
    } catch (IOException e) {
      throw new EsException(e);
    }
    List<NameSuggestion> suggestions = new ArrayList<>();
    SuggestionFactory factory = new SuggestionFactory(request);
    esResponse.getHits().getHits().forEach(hit -> {
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
