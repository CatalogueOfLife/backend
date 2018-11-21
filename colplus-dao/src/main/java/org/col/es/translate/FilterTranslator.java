package org.col.es.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.util.VocabularyUtils;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.IsNotNullQuery;
import org.col.es.query.IsNullQuery;
import org.col.es.query.Query;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;

import static org.col.api.search.NameSearchRequest.NOT_NULL_VALUE;
import static org.col.api.search.NameSearchRequest.NULL_VALUE;

/**
 * Translates a single request parameter into an Elasticsearch query. If the parameter is multi-valued and contains no symbolic values (like
 * _NULL), a terms query will be generated; if it has a mixture of literal and symbolic values, an OR query will generated.
 */
class FilterTranslator {

  private final NameSearchRequest request;

  FilterTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Query translate(NameSearchParameter param) throws InvalidQueryException {
    List<Query> queries = new ArrayList<>();
    String field = EsFieldLookup.INSTANCE.lookup(param);
    if (containsNullValue(param)) {
      queries.add(new IsNullQuery(field));
    }
    if (containsNotNullValue(param)) {
      queries.add(new IsNotNullQuery(field));
    }
    // (Not very clever to have both, but OK)
    List<?> paramValues = getLiteralValues(param);
    if (paramValues.size() == 1) {
      queries.add(new TermQuery(field, paramValues.get(0)));
    } else if (paramValues.size() > 1) {
      queries.add(new TermsQuery(field, paramValues));
    }
    if (queries.size() == 1) {
      return queries.get(0);
    }
    return queries.stream().collect(BoolQuery::new, BoolQuery::should, BoolQuery::should);
  }

  /*
   * Get all non-symbolic values for one single single query parameter
   */
  @SuppressWarnings("unchecked")
  private List<?> getLiteralValues(NameSearchParameter param) throws InvalidQueryException {
    if (param.type() == String.class) {
      return request.getFilterValue(param).stream().filter(this::isLiteral).collect(Collectors.toList());
    }
    if (param.type() == Integer.class) {
      try {
        return request.getFilterValue(param).stream().filter(this::isLiteral).map(Integer::valueOf).collect(Collectors.toList());
      } catch (NumberFormatException e) {
        throw new InvalidQueryException("Non-integer value for parameter " + param);
      }
    }
    if (Enum.class.isAssignableFrom(param.type())) {
      try {
        return request.getFilterValue(param)
            .stream()
            .filter(this::isLiteral)
            .map(val -> VocabularyUtils.lookupEnum(val, (Class<? extends Enum<?>>) param.type()).ordinal())
            .collect(Collectors.toList());
      } catch (IllegalArgumentException e) {
        throw new InvalidQueryException(e.getMessage());
      }
    }
    throw new AssertionError("Unexpected parameter type: " + param.type());
  }

  private boolean isLiteral(String s) {
    return !s.equals(NULL_VALUE) && !s.equals(NOT_NULL_VALUE);
  }

  // Is one of the values of a query parameter the symbol for IS NULL?
  private boolean containsNullValue(NameSearchParameter param) {
    return request.getFilterValue(param).stream().anyMatch(s -> s.equals(NULL_VALUE));
  }

  // Is one of the values of a query parameter the symbol for IS NOT NULL?
  private boolean containsNotNullValue(NameSearchParameter param) {
    return request.getFilterValue(param).stream().anyMatch(s -> s.equals(NOT_NULL_VALUE));
  }
}
