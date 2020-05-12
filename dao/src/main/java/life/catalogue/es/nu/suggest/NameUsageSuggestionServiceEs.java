package life.catalogue.es.nu.suggest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.es.EsException;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsUtil;
import life.catalogue.es.NameUsageSuggestionService;
import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.nu.NameUsageQueryService;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.response.EsResponse;

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
    request.setSearchTerms(EsUtil.getSearchTerms(client, index, Analyzer.SCINAME_AUTO_COMPLETE, request.getQ()));
    RequestTranslator translator = new RequestTranslator(request);
    EsSearchRequest query = translator.translate();
    EsResponse<EsNameUsage> esResponse;
    try {
      esResponse = executeSearchRequest(index, query);
    } catch (IOException e) {
      throw new EsException(e);
    }
    List<NameUsageSuggestion> suggestions = new ArrayList<>();
    SearchHitConverter factory = new SearchHitConverter(request);
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
      throw new IllegalArgumentException("Missing q parameter");
    }
    if (request.getDatasetKey() == null || request.getDatasetKey() < 1) {
      throw new IllegalArgumentException("Missing/invalid datasetKey parameter");
    }
  }

}
