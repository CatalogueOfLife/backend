package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;

/**
 * Executes autocomplete-type queries against the scientific name field.
 */
class SimplePrefixMatcher extends SimpleMatcher implements PrefixMatcher {

  SimplePrefixMatcher(NameUsageRequest request) {
    super(request);
  }

}
