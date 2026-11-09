package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NULL;

/**
 * Translates all query parameters except the "q" parameter into an Elasticsearch query.
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
          .filter(p -> p != DECISION_MODE && p != PROJECT_KEY)
          .map(ft::translate)
          .forEach(subqueries::add);
    }
    subqueries.addAll(processMinMaxRank(request));
    if (request.hasExcludedDatasets()) {
      subqueries.add(excludeDatasets(request.getExcludedDatasetKeys()));
    }
    if (subqueries.size() == 1) {
      return subqueries.get(0);
    }
    final List<Query> filters = subqueries;
    return Query.of(q -> q.bool(b -> {
      filters.forEach(b::filter);
      return b;
    }));
  }

  private List<Query> processDecisionFilters() {
    final String path = "decisions";
    if (request.hasFilter(DECISION_MODE)) {
      FilterTranslator ft = new FilterTranslator(request);
      if (request.getFilterValue(DECISION_MODE).equals(IS_NULL)) {
        Query catQuery = ft.translate(PROJECT_KEY);
        return List.of(Query.of(q -> q.bool(b -> b
          .mustNot(mn -> mn.nested(n -> n.path(path).query(catQuery)))
        )));
      } else if (request.getFilterValue(DECISION_MODE).equals(IS_NOT_NULL)) {
        Query catQuery = ft.translate(PROJECT_KEY);
        return List.of(Query.of(q -> q.bool(b -> b
          .must(m -> m.nested(n -> n.path(path).query(catQuery)))
        )));
      } else {
        Query modeQuery = ft.translate(DECISION_MODE);
        Query catQuery = ft.translate(PROJECT_KEY);
        return List.of(Query.of(q -> q.nested(n -> n.path(path).query(
          Query.of(inner -> inner.bool(b -> b.filter(modeQuery).filter(catQuery)))
        ))));
      }
    }
    return Collections.emptyList();
  }

  public static List<Query> processMinMaxRank(NameUsageRequest request) {
    String rankField = FieldLookup.INSTANCE.lookupSingle(RANK);
    if (request.getMinRank() != null) {
      int minOrd = request.getMinRank().ordinal();
      if (request.getMaxRank() != null) {
        int maxOrd = request.getMaxRank().ordinal();
        return List.of(Query.of(q -> q.range(r -> r
          .number(n -> n.field(rankField)
            .lte((double) minOrd)
            .gte((double) maxOrd))
        )));
      }
      return List.of(Query.of(q -> q.range(r -> r
        .number(n -> n.field(rankField).lte((double) minOrd))
      )));
    } else if (request.getMaxRank() != null) {
      int maxOrd = request.getMaxRank().ordinal();
      return List.of(Query.of(q -> q.range(r -> r
        .number(n -> n.field(rankField).gte((double) maxOrd))
      )));
    }
    return Collections.emptyList();
  }

  public static boolean mustGenerateFilters(NameUsageRequest request) {
    return request.getFilters().size() > 1 ||
      request.getFilters().size() == 1 && !request.hasFilter(PROJECT_KEY) ||
      request.getMinRank() != null ||
      request.getMaxRank() != null ||
      request.hasExcludedDatasets();
  }

  /**
   * The index holds private datasets as well and has no idea which are private, so a search across datasets leaves
   * out those the caller may not read. As the set of private datasets rarely changes, the filter is the same for
   * every anonymous request and Elasticsearch can cache it.
   */
  private static Query excludeDatasets(Set<Integer> keys) {
    String field = FieldLookup.INSTANCE.lookupSingle(DATASET_KEY);
    List<FieldValue> values = keys.stream()
      .sorted()
      .map(k -> FieldValue.of(String.valueOf(k)))
      .toList();
    return Query.of(q -> q.bool(b -> b
      .mustNot(mn -> mn.terms(t -> t.field(field).terms(tv -> tv.value(values))))
    ));
  }
}
