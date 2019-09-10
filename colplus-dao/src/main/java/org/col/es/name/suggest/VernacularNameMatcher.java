package org.col.es.name.suggest;

import java.util.List;

import org.col.api.search.NameSuggestRequest;

class VernacularNameMatcher {

  @SuppressWarnings("unused")
  private final NameSuggestRequest request;

  VernacularNameMatcher(NameSuggestRequest request) {
    this.request = request;
  }

  String getMatch(List<String> names) {
    // TODO implement
    return names.get(0);
  }

}
