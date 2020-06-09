package life.catalogue.es.nu.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.nu.NameUsageFieldLookup;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.IsNotNullQuery;
import life.catalogue.es.query.NestedQuery;
import life.catalogue.es.query.Query;
import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DECISION_MODE;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NULL;

/**
 * Translates all query parameters except the "q" parameter into an Elasticsearch query. Unless there is just one query parameter, this will
 * result in an AND query. For example: ?rank=genus&nom_status=available is translated into (rank=genus AND nom_status=available). If a
 * request parameter is multi-valued, this will result in a nested OR query. For example: ?nom_status=available&rank=family&rank=genus is
 * translated into (nom_status=available AND (rank=family OR rank=genus))
 */
class FiltersTranslator {

  private final NameUsageSearchRequest request;

  FiltersTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Query translate() throws InvalidQueryException {
    FilterTranslator ft = new FilterTranslator(request);
    Set<NameUsageSearchParameter> params = EnumSet.copyOf(request.getFilters().keySet());
    List<Query> subqueries = new ArrayList<>(params.size());
    subqueries.addAll(processDecisionFilters());
    request.getFilters().keySet().stream()
        .filter(p -> p != DECISION_MODE && p != CATALOGUE_KEY)
        .map(ft::translate)
        .forEach(subqueries::add);
    if (subqueries.size() == 1) {
      return subqueries.get(0);
    }
    BoolQuery query = new BoolQuery();
    subqueries.forEach(query::filter);
    return query;
  }

  // See also RequestValidator class
  /*
   * Note: IS NULL queries on nested documents are tricky and cannot be left to the standard FilterTranslator. We cannot use an IsNullQuery.
   * We must wrap an isNotNull into a NestedQuery and then wrap the NestedQuery into a mustNot query.
   */
  private List<Query> processDecisionFilters() {
    final String path = "decisions";
    if (request.hasFilter(DECISION_MODE)) {
      if (request.getFilterValue(DECISION_MODE).equals(IS_NULL)) {
        String field = NameUsageFieldLookup.INSTANCE.lookup(DECISION_MODE);
        NestedQuery nestedQuery = new NestedQuery(path, new IsNotNullQuery(field));
        return List.of(new BoolQuery().mustNot(nestedQuery));
      }
      return List.of(new NestedQuery(path, new FilterTranslator(request).translate(DECISION_MODE)));
    }
    return Collections.emptyList();
  }

}
