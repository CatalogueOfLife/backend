package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.PrefixQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameAutoCompleteQuery;
import static life.catalogue.es.query.AbstractMatchQuery.Operator.AND;

/**
 * Executes an autocomplete-type query against the scientific name field as well as the normalized versions of the scientific name's
 * epithets.
 */
class FuzzyPrefixQMatcher extends FuzzyQMatcher {

  FuzzyPrefixQMatcher(NameUsageRequest request) {
    super(request);
  }

  Query matchAsEpithet(String field, String term) {
    if (term.length() > MAX_NGRAM_SIZE) {
      return new PrefixQuery(field, term).withBoost(0.1 * term.length());
    }
    return new SciNameAutoCompleteQuery(field, term).withOperator(AND).withBoost(3.5);
  }

}
