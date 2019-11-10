package org.col.es.name.suggest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.col.api.search.NameUsageSuggestRequest;
import org.col.api.search.NameUsageSuggestResponse;
import org.col.api.search.NameUsageSuggestion;
import org.col.es.EsException;
import org.col.es.name.NameUsageQueryService;
import org.col.es.name.NameUsageEsResponse;
import org.col.es.name.search.NameUsageSearchServiceEs;
import org.col.es.query.EsSearchRequest;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameUsageSuggestionServiceEs extends NameUsageQueryService implements NameUsageSuggestionService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);

  public NameUsageSuggestionServiceEs(String indexName, RestClient client) {
    super(indexName, client);
  }

  @Override
  public NameUsageSuggestResponse suggest(NameUsageSuggestRequest request) {
    validateRequest(request);
    RequestTranslator translator = new RequestTranslator(request);
    EsSearchRequest query = translator.translate();
    NameUsageEsResponse esResponse;
    try {
      esResponse = executeSearchRequest(index, query);
    } catch (IOException e) {
      throw new EsException(e);
    }
    List<NameUsageSuggestion> suggestions = new ArrayList<>();
    SuggestionFactory factory = new SuggestionFactory(request);
    esResponse.getHits().getHits().forEach(hit -> {
      if (hit.matchedQuery(QTranslator.SN_QUERY_NAME)) {
        suggestions.add(factory.createSuggestion(hit, false));
      }
      if (hit.matchedQuery(QTranslator.VN_QUERY_NAME)) {
        suggestions.add(factory.createSuggestion(hit, true));
      }
    });
    return new NameUsageSuggestResponse(suggestions);
  }

  private static void validateRequest(NameUsageSuggestRequest request) {
    if (StringUtils.isBlank(request.getQ())) {
      throw new EsException("Missing q parameter");
    }
    if (request.getDatasetKey() == null || request.getDatasetKey() < 1) {
      throw new EsException("Missing/invalid datasetKey parameter");
    }
  }

}
