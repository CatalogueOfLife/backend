package life.catalogue.es.nu.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.NestedQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.RangeQuery;
import life.catalogue.es.query.TermQuery;
import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DECISION_MODE;
import static life.catalogue.api.search.NameUsageSearchParameter.RANK;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NULL;

/**
 * <p>
 * Translates all query parameters except the "q" parameter into an Elasticsearch query. Unless there is just one query parameter, this will
 * result in an AND query. For example: <code>?rank=genus&nom_status=available</code> is translated into:
 * </p>
 * 
 * <pre>
 * (rank=genus AND nom_status=available)
 * </pre>
 * <p>
 * request parameter is multi-valued, this will result in a nested OR query. For example:
 * <code>?nom_status=available&rank=family&rank=genus</code> is translated into:
 * </p>
 * 
 * <pre>
 * (nom_status=available AND (rank=family OR rank=genus))
 * </pre>
 * <p>
 * The translation of any single parameter if left to the {@link FilterTranslator} except for decision-related filters as they require a lot
 * of extra logic, and the minRank/maxRank filters as they don't translate into simple term queries.
 * </p>
 */
class FiltersTranslator {

  private final NameUsageSearchRequest request;

  FiltersTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Query translate() throws InvalidQueryException {
    List<Query> subqueries = new ArrayList<>();
    if (request.hasFilters()) {
      subqueries.addAll(processDecisionFilters());
      FilterTranslator ft = new FilterTranslator(request);
      request.getFilters().keySet().stream()
          .filter(p -> p != DECISION_MODE && p != CATALOGUE_KEY)
          .map(ft::translate)
          .forEach(subqueries::add);
    }
    subqueries.addAll(processMinMaxRank());
    if (subqueries.size() == 1) {
      return subqueries.get(0);
    }
    BoolQuery query = new BoolQuery();
    subqueries.forEach(query::filter);
    return query;
  }

  private List<Query> processDecisionFilters() {
    final String path = "decisions";
    if (request.hasFilter(DECISION_MODE)) {
      FilterTranslator ft = new FilterTranslator(request);
      if (request.getFilterValue(DECISION_MODE).equals(IS_NULL)) {
        if (request.hasFilter(CATALOGUE_KEY)) {
          // The user wants nameusages which do *NOT* have a decision with the provided catalog key!
          return List.of(new BoolQuery().mustNot(new NestedQuery(path, ft.translate(CATALOGUE_KEY))));
        }
        return List.of(new TermQuery("decisionCount", 0));
      } else if (request.getFilterValue(DECISION_MODE).equals(IS_NOT_NULL)) {
        return List.of(new RangeQuery<Integer>("decisionCount").greaterThan(0));
      } else if (request.hasFilter(CATALOGUE_KEY)) {
        return List.of(new NestedQuery(path, BoolQuery.withFilters(ft.translate(DECISION_MODE), ft.translate(CATALOGUE_KEY))));
      }
      return List.of(new NestedQuery(path, ft.translate(DECISION_MODE)));
    }
    return Collections.emptyList();
  }

  // Little gotcha here: the higher the rank, the lower the ordinal!
  private List<Query> processMinMaxRank() {
    if (request.getMinRank() != null) {
      if (request.getMaxRank() != null) {
        return List.of(RangeQuery.on(RANK)
            .lessOrEqual(request.getMinRank().ordinal())
            .greaterOrEqual(request.getMaxRank().ordinal()));
      }
      return List.of(RangeQuery.on(RANK).lessOrEqual(request.getMinRank().ordinal()));
    } else if (request.getMaxRank() != null) {
      return List.of(RangeQuery.on(RANK).greaterOrEqual(request.getMaxRank().ordinal()));
    }
    return Collections.emptyList();
  }

}
