package life.catalogue.es.name;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameCaseInsensitiveQuery;

/**
 * Executes match-whole-words-only query against the scientific name field as well as the normalized versions of the scientific name's
 * epithets.
 */
class FuzzyWholeWordQMatcher extends FuzzyQMatcher {

  FuzzyWholeWordQMatcher(NameUsageRequest request) {
    super(request);
  }

  Query matchAsEpithet(String field, String term) {
    return new SciNameCaseInsensitiveQuery(field, term);
  }

}
