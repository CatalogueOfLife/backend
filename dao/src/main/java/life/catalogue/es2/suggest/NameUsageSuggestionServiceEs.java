package life.catalogue.es2.suggest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.validation.constraints.NotNull;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;

import java.util.List;

public class NameUsageSuggestionServiceEs implements NameUsageSuggestionService {
  public NameUsageSuggestionServiceEs(@NotNull String name, ElasticsearchClient esClient) {

  }

  @Override
  public List<NameUsageSuggestion> suggest(NameUsageSuggestRequest request) {
    // .size(10)
    //.trackTotalHits(false)
    return List.of();
  }
}
