package life.catalogue.es;

import java.util.Collections;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;

public interface NameUsageSuggestionService {

  static NameUsageSuggestionService passThru() {
    return (req) -> new NameUsageSuggestResponse(Collections.emptyList());
  }

  NameUsageSuggestResponse suggest(NameUsageSuggestRequest request);

}
