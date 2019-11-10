package org.col.es.name.suggest;

import java.util.Collections;

import org.col.api.search.NameUsageSuggestRequest;
import org.col.api.search.NameUsageSuggestResponse;

public interface NameUsageSuggestionService {

  static NameUsageSuggestionService passThru() {
    return (req) -> new NameUsageSuggestResponse(Collections.emptyList());
  }

  NameUsageSuggestResponse suggest(NameUsageSuggestRequest request);

}
