package life.catalogue.es.name;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.DisMaxQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameCaseInsensitiveQuery;
import life.catalogue.es.query.SciNameWholeWordsQuery;

/**
 * Executes a match-whole-words-only query against the scientific name.
 */
class WholeWordQMatcher extends QMatcher {

  static DisMaxQuery baseQuery(NameUsageRequest request) {
    return new DisMaxQuery()
        // Make sure exact matches (even on small scientific names like genus "Ara") always prevail
        .subquery(new SciNameCaseInsensitiveQuery(FLD_SCINAME, request.getQ()).withBoost(100.0))
        .subquery(new SciNameWholeWordsQuery(FLD_SCINAME, request.getQ()));
  }

  WholeWordQMatcher(NameUsageRequest request) {
    super(request);
  }

  public Query getScientificNameQuery() {
    return baseQuery(request);
  }

}
