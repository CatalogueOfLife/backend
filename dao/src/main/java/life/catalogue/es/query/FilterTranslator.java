package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static java.util.stream.Collectors.toList;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NULL;

/**
 * Translates a single request parameter into an Elasticsearch query.
 */
public class FilterTranslator {

  private final NameUsageRequest request;

  public FilterTranslator(NameUsageRequest request) {
    this.request = request;
  }

  public Query translate(NameUsageSearchParameter param) throws InvalidQueryException {
    List<Query> queries = new ArrayList<>();
    String[] fields = FieldLookup.INSTANCE.lookup(param);
    for (String field : fields) {
      if (containsNullValue(param)) {
        // IS NULL = must not exist
        queries.add(Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.exists(e -> e.field(field))))));
      }
      if (containsNotNullValue(param)) {
        // IS NOT NULL = must exist
        queries.add(Query.of(q -> q.exists(e -> e.field(field))));
      }
      List<?> paramValues = getLiteralValues(param);
      if (paramValues.size() == 1) {
        Object val = paramValues.get(0);
        queries.add(Query.of(q -> q.term(t -> t.field(field).value(toFieldValue(val)))));
      } else if (paramValues.size() > 1) {
        List<FieldValue> fvs = paramValues.stream().map(FilterTranslator::toFieldValue).collect(toList());
        queries.add(Query.of(q -> q.terms(t -> t.field(field).terms(tv -> tv.value(fvs)))));
      }
      if (queries.size() == 1) {
        return queries.get(0);
      }
    }
    // Multiple conditions -> OR (should)
    final List<Query> finalQueries = queries;
    return Query.of(q -> q.bool(b -> {
      finalQueries.forEach(b::should);
      return b;
    }));
  }

  static FieldValue toFieldValue(Object val) {
    if (val instanceof Number n) {
      if (val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Byte) {
        return FieldValue.of(n.longValue());
      }
      return FieldValue.of(n.doubleValue());
    } else if (val instanceof Boolean b) {
      return FieldValue.of(b);
    } else if (val instanceof Rank r) {
      return FieldValue.of(r.ordinal());
    } else if (val instanceof Enum<?> e) {
      return FieldValue.of(e.name());
    } else {
      return FieldValue.of(val.toString());
    }
  }

  private List<?> getLiteralValues(NameUsageSearchParameter param) throws InvalidQueryException {
    return request.hasFilter(param) ? request.getFilterValues(param).stream().filter(this::isLiteral).collect(toList()) : List.of();
  }

  private boolean isLiteral(Object o) {
    return !o.equals(IS_NULL) && !o.equals(IS_NOT_NULL);
  }

  private boolean containsNullValue(NameUsageSearchParameter param) {
    return request.hasFilter(param) && request.getFilterValues(param).contains(IS_NULL);
  }

  private boolean containsNotNullValue(NameUsageSearchParameter param) {
    return request.hasFilter(param) && request.getFilterValues(param).contains(IS_NOT_NULL);
  }
}
