package life.catalogue.es.nu.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.es.*;
import life.catalogue.es.nu.NameUsageQueryService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

import com.google.common.annotations.VisibleForTesting;

import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;

public class NameUsageSuggestionServiceEs extends NameUsageQueryService implements NameUsageSuggestionService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSuggestionServiceEs.class);

  public NameUsageSuggestionServiceEs(String indexName, ElasticsearchClient client) {
    super(indexName, client);
  }

  @Override
  public List<NameUsageSuggestion> suggest(NameUsageSuggestRequest request) {
    try {
      return suggest(index, request);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @VisibleForTesting
  public List<NameUsageSuggestion> suggest(String index, NameUsageSuggestRequest request) throws IOException {
    validateRequest(request);
    String[] terms = EsUtil.getSearchTerms(client, index, "sciname_autocomplete_querytime", request.getQ());
    request.setSciNameSearchTerms(terms);
    RequestTranslator translator = new RequestTranslator(request);
    SearchRequest searchRequest = translator.translate(index);
    SearchResponse<EsNameUsage> esResponse = executeSearchRequest(index, searchRequest);
    final List<NameUsageSuggestion> suggestions = new ArrayList<>();
    SearchHitConverter converter = new SearchHitConverter();
    for (Hit<EsNameUsage> hit : esResponse.hits().hits()) {
      if (hit.source() != null) {
        suggestions.add(converter.createSuggestion(hit));
      }
    }
    return suggestions;
  }

  private static void validateRequest(NameUsageSuggestRequest request) {
    if (StringUtils.isBlank(request.getQ())) {
      throw invalidRequest("Missing q parameter");
    }
    if (!request.hasFilter(DATASET_KEY) || request.getFilters().get(DATASET_KEY).size() > 1) {
      throw invalidRequest("A single dataset key must be specified");
    }
  }

  private static InvalidQueryException invalidRequest(String msg, Object... msgArgs) {
    msg = "Invalid suggest request. " + String.format(msg, msgArgs);
    return new InvalidQueryException(msg);
  }
}
