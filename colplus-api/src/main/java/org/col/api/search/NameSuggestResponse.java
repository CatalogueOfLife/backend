package org.col.api.search;

import java.util.List;

public class NameSuggestResponse {

  List<NameSuggestion> suggestions;

  public List<NameSuggestion> getSuggestions() {
    return suggestions;
  }

  public void setSuggestions(List<NameSuggestion> suggestions) {
    this.suggestions = suggestions;
  }
}
