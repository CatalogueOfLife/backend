package life.catalogue.es;

import life.catalogue.api.search.NameUsageRequest;

class FuzzyPrefixMatcher extends FuzzyMatcher implements PrefixMatcher {

  FuzzyPrefixMatcher(NameUsageRequest request) {
    super(request);
  }

}
