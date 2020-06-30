package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;

class FuzzyPrefixMatcher extends FuzzyMatcher implements PrefixMatcher {

  FuzzyPrefixMatcher(NameUsageRequest request) {
    super(request);
  }

}
