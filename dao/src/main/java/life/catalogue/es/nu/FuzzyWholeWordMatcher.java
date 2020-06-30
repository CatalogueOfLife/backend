package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;

/**
 * Executes match-whole-words-only query against the scientific name field as well as the normalized versions of the scientific name's
 * epithets.
 */
class FuzzyWholeWordMatcher extends FuzzyMatcher implements WholeWordMatcher {

  FuzzyWholeWordMatcher(NameUsageRequest request) {
    super(request);
  }

}
