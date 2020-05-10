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

  private List<Query> processDecisionFilters() {
    final String path = "decisions";
    if (request.hasFilter(DECISION_MODE)) {
      if (request.hasFilter(CATALOGUE_KEY)) {
        FilterTranslator ft = new FilterTranslator(request);
        BoolQuery boolQuery = BoolQuery.withFilters(ft.translate(CATALOGUE_KEY), ft.translate(DECISION_MODE));
        return List.of(new NestedQuery(path, boolQuery));
      }
      List<Object> decisionModes = request.getFilterValues(DECISION_MODE);
      /*
       * IS NULL queries on nested documents are very tricky and cannot be left to the standard FilterTranslator. We cannot simply use an
       * IsNullQuery. Instead we wrap a NestedQuery around an is<b>Not</b>Null query and then wrap the NestedQuery within a mustNot query. If you
       * do it the other way round it won't work. Strictly speaking we face the same problem above, when both catalog key and decision mode
       * are present. But there clients deserve the punishment of asking for catalog key IS NULL and decision mode IS NULL.
       */
      if (decisionModes.contains(IS_NULL)) {
        if (decisionModes.size() > 1) {
          // This just gets way too complicated and there's probably no use case for it
          throw new InvalidQueryException("IS NULL query on decision_mode must be the only query on that field");
        }
        String field = NameUsageFieldLookup.INSTANCE.lookup(DECISION_MODE);
        NestedQuery nestedQuery = new NestedQuery(path, new IsNotNullQuery(field));
        return List.of(new BoolQuery().mustNot(nestedQuery));
      }
      // None of the values for DECISION_MODE is _IS_NULL, so we can use the FilterTranslator.
      return List.of(new NestedQuery(path, new FilterTranslator(request).translate(DECISION_MODE)));
    }
    // Catalog key alone is ignored. It's only going to be used later on, when post-processing the query result.
    return Collections.emptyList();
  }

}
