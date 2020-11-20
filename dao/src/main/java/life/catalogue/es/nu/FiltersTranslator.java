package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.NestedQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.RangeQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static life.catalogue.api.search.NameUsageSearchParameter.*;
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
public class FiltersTranslator {

  private final NameUsageRequest request;

  public FiltersTranslator(NameUsageRequest request) {
    this.request = request;
  }

  public Query translate() throws InvalidQueryException {
    List<Query> subqueries = new ArrayList<>();
    if (request.hasFilters()) {
      subqueries.addAll(processDecisionFilters());
      FilterTranslator ft = new FilterTranslator(request);
      request.getFilters().keySet().stream()
          .filter(p -> p != DECISION_MODE && p != CATALOGUE_KEY)
          .map(ft::translate)
          .forEach(subqueries::add);
    }
    subqueries.addAll(processMinMaxRank(request));
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
      // the request validator mandates a single catalogue key in this case!
      FilterTranslator ft = new FilterTranslator(request);
      if (request.getFilterValue(DECISION_MODE).equals(IS_NULL)) {
        // The user wants nameusages which do *NOT* have a decision with the provided catalog key!
        return List.of(new BoolQuery().mustNot(new NestedQuery(path, ft.translate(CATALOGUE_KEY))));
      } else if (request.getFilterValue(DECISION_MODE).equals(IS_NOT_NULL)) {
        return List.of(new BoolQuery().must(new NestedQuery(path, ft.translate(CATALOGUE_KEY))));
      } else {
        return List.of(new NestedQuery(path, BoolQuery.withFilters(ft.translate(DECISION_MODE), ft.translate(CATALOGUE_KEY))));
      }
    }
    return Collections.emptyList();
  }

  // Little gotcha here: the higher the rank, the lower the ordinal!
  public static List<Query> processMinMaxRank(NameUsageRequest request) {
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

  public static boolean mustGenerateFilters(NameUsageRequest request) {
    return request.getFilters().size() > 1 ||
      request.getFilters().size() == 1 && !request.hasFilter(CATALOGUE_KEY) ||
      request.getMinRank() != null ||
      request.getMaxRank() != null;
  }
}
