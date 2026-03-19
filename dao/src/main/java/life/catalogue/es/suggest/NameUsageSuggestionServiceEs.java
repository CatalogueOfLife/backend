package life.catalogue.es.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsException;
import life.catalogue.es.EsQueryService;

import java.io.IOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import jakarta.validation.constraints.NotNull;

public class NameUsageSuggestionServiceEs extends EsQueryService implements NameUsageSuggestionService {

  public NameUsageSuggestionServiceEs(@NotNull String name, ElasticsearchClient esClient) {
    super(name, esClient);
  }

  @Override
  public List<NameUsageSuggestion> suggest(NameUsageSuggestRequest request) {
    try {
      new SuggestRequestValidator(request).validateRequest();
      var translator = new SuggestRequestTranslator(request);
      SearchRequest esSearchRequest = translator.translateRequest(index);
      SearchResponse<NameUsageWrapper> esResponse = client.search(esSearchRequest, NameUsageWrapper.class);
      return new ResponseConverter(esResponse).convertEsResponse();

    } catch (IOException e) {
      throw new EsException(e);
    }
  }

}
