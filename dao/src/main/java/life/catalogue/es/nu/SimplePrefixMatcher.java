package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;

/**
 * Executes autocomplete-type queries against the scientific name field without normalization of the search terms.
 */
class SimplePrefixMatcher extends SimpleMatcher implements PrefixMatcher {

  SimplePrefixMatcher(NameUsageRequest request) {
    super(request);
  }

}
