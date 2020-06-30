package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;

/**
 * Executes a match-whole-words-only query against the scientific name.
 */
class SimpleWholeWordMatcher extends SimpleMatcher implements WholeWordMatcher {

  SimpleWholeWordMatcher(NameUsageRequest request) {
    super(request);
  }

}
