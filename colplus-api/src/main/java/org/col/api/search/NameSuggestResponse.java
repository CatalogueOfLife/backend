package org.col.api.search;

import java.util.List;

/**
 * The response object returned from a name suggestion request (a&period;k&period;a&period; auto-completion).
 * 
 */
public class NameSuggestResponse {

  private List<NameSuggestion> suggestions;

  public NameSuggestResponse() {}

  public NameSuggestResponse(List<NameSuggestion> suggestions) {
    this.suggestions = suggestions;
  }

  public List<NameSuggestion> getSuggestions() {
    return suggestions;
  }

  public void setSuggestions(List<NameSuggestion> suggestions) {
    this.suggestions = suggestions;
  }
}
