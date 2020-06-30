package life.catalogue.es.nu;

import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameEqualsQuery;

/**
 * Mix-in interface for whole-word matching
 */
interface WholeWordMatcher extends MatcherMixIn {

  @Override
  default Query matchAsEpithet(String field, String term) {
    return new SciNameEqualsQuery(field, term).withBoost(5.0);
  }

}
