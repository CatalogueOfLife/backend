package life.catalogue.es.nu.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import life.catalogue.api.search.NameUsageSearchParameter;
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
    subqueries.addAll(processMinMaxRank());
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

  private List<Query> processDecisionFilters() {
    final String path = "decisions";
    if (request.hasFilter(DECISION_MODE)) {
      FilterTranslator ft = new FilterTranslator(request);
      if (request.getFilterValue(DECISION_MODE).equals(IS_NULL)) {
        if (request.hasFilter(CATALOGUE_KEY)) {
          if (request.getFilterValue(CATALOGUE_KEY).equals(IS_NULL)) {
            /*
             * The user is confused. There cannot be nameusage documents with a decision subdocument whose catalogue key is null. There can
             * be nameusage documents without any decision at all, but this is not the way to ask for them.
             */
            throw new InvalidQueryException("Decision mode and catalogue key parameters must not both be null");
          } // else, as per convention, the user wants nameusages which do NOT have a decision with the provided catalog key!
          return List.of(new BoolQuery().mustNot(new NestedQuery(path, ft.translate(CATALOGUE_KEY))));
        } // else the user wants nameusages without any decisions at all:
        return List.of(new TermQuery("decisionCount", 0));
      }
      if (request.hasFilter(CATALOGUE_KEY)) {
        return List.of(new NestedQuery(path, BoolQuery.withFilters(ft.translate(DECISION_MODE), ft.translate(CATALOGUE_KEY))));
      }
      return List.of(new NestedQuery(path, ft.translate(DECISION_MODE)));
    }
    return Collections.emptyList();
  }

  private List<Query> processMinMaxRank() {
    if (request.getMinRank() != null) { // NB not a NameUsageParameter, but still a filter
      if (request.getMaxRank() != null) {
        return List.of(RangeQuery.on(RANK).greaterOrEqual(request.getMinRank().ordinal()).lessOrEqual(request.getMaxRank().ordinal()));
      }
      return List.of(RangeQuery.on(RANK).greaterOrEqual(request.getMinRank().ordinal()));
    } else if (request.getMaxRank() != null) {
      return List.of(RangeQuery.on(RANK).lessOrEqual(request.getMaxRank().ordinal()));
    }
    return Collections.emptyList();
  }

}
