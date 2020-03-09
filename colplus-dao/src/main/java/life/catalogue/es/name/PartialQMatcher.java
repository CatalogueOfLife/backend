package life.catalogue.es.name;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.DisMaxQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameAutoCompleteQuery;
import life.catalogue.es.query.SciNameCaseInsensitiveQuery;

/**
 * Executes autocomplete-type queries against the scientific name field.
 */
class PartialQMatcher extends QMatcher {

  PartialQMatcher(NameUsageRequest request) {
    super(request);
  }

  public Query getScientificNameQuery() {
    return new DisMaxQuery()
        .subquery(new SciNameCaseInsensitiveQuery(FLD_SCINAME, request.getQ()).withBoost(100.0))
        .subquery(new SciNameAutoCompleteQuery(FLD_SCINAME, request.getQ()));
  }

}
