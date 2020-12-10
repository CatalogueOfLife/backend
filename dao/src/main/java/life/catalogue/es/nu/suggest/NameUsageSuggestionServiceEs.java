package life.catalogue.es.nu.suggest;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.es.*;
import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.nu.NameUsageQueryService;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.response.EsResponse;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;

public class NameUsageSuggestionServiceEs extends NameUsageQueryService implements NameUsageSuggestionService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSuggestionServiceEs.class);

  public NameUsageSuggestionServiceEs(String indexName, RestClient client) {
    super(indexName, client);
  }

  @Override
  public NameUsageSuggestResponse suggest(NameUsageSuggestRequest request) {
    try {
      return suggest(index, request);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @VisibleForTesting
  public NameUsageSuggestResponse suggest(String index, NameUsageSuggestRequest request) throws IOException {
    validateRequest(request);
    String[] terms = EsUtil.getSearchTerms(client, index, Analyzer.SCINAME_AUTO_COMPLETE, request.getQ());
    request.setSciNameSearchTerms(terms);
    RequestTranslator translator = new RequestTranslator(request);
    EsSearchRequest query = translator.translate();
    EsResponse<EsNameUsage> esResponse = executeSearchRequest(index, query);
    List<NameUsageSuggestion> suggestions = new ArrayList<>();
    SearchHitConverter suggestionFactory = new SearchHitConverter();
    esResponse.getHits().getHits().forEach(hit -> {
      if (hit.matchedQuery(QTranslator.SN_QUERY_NAME)) {
        suggestions.add(suggestionFactory.createSuggestion(hit));
      }
    });
    return new NameUsageSuggestResponse(suggestions);
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
