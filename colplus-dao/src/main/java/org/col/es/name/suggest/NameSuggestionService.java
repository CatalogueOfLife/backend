package org.col.es.name.suggest;

import java.util.Collections;

import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;

public interface NameSuggestionService {

  static NameSuggestionService passThru() {
    return (req) -> new NameSuggestResponse(Collections.emptyList());
  }

  NameSuggestResponse suggest(NameSuggestRequest request);

}
