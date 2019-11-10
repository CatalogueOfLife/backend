package org.col.api.search;

import java.util.List;

/**
 * The response object returned from a name suggestion request (a&period;k&period;a&period; auto-completion).
 * 
 */
public class NameUsageSuggestResponse {

  private List<NameUsageSuggestion> suggestions;

  public NameUsageSuggestResponse() {}

  public NameUsageSuggestResponse(List<NameUsageSuggestion> suggestions) {
    this.suggestions = suggestions;
  }

  public List<NameUsageSuggestion> getSuggestions() {
    return suggestions;
  }

  public void setSuggestions(List<NameUsageSuggestion> suggestions) {
    this.suggestions = suggestions;
  }
}
