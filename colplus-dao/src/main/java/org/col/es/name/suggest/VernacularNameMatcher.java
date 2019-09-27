package org.col.es.name.suggest;

import java.util.Arrays;
import java.util.List;

import org.col.api.search.NameSuggestRequest;

/**
 * Determines which of the vernacular names in a usage document actually matched the search phrase (q). Using named
 * queries we can identify the document was returned because the q could be matched against its scientific name or
 * against its vernacular names. Unfortunately if the document contains multiple vernacular names, we still don't which
 * one constituted the match.
 *
 */
class VernacularNameMatcher {

  private final String[] terms;

  VernacularNameMatcher(NameSuggestRequest request) {
    this.terms = tokenize(request.getQ());
  }

  String getMatch(List<String> names) {
    // TODO make more sophisticated.
    for (String name : names) {
      String[] words = tokenize(name);
      for (String word : words) {
        for (String term : terms) {
          if (word.contains(term)) {
            return word;
          }
        }
      }
    } // Huh?
    return names.get(0);
  }

  private static String[] tokenize(String str) {
    return Arrays.stream(str.split("\\W")).filter(s -> !s.isEmpty()).map(String::toLowerCase).toArray(String[]::new);
  }

}
