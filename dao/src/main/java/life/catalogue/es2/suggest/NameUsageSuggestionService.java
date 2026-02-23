package life.catalogue.es2.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;

import java.util.Collections;
import java.util.List;

public interface NameUsageSuggestionService {

  static NameUsageSuggestionService passThru() {
    return (req) -> Collections.emptyList();
  }

  List<NameUsageSuggestion> suggest(NameUsageSuggestRequest request);

}
