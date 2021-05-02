package life.catalogue.es;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;

import java.util.Collections;

public interface NameUsageSuggestionService {

  static NameUsageSuggestionService passThru() {
    return (req) -> new NameUsageSuggestResponse(Collections.emptyList());
  }

  NameUsageSuggestResponse suggest(NameUsageSuggestRequest request);

}
