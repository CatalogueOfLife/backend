package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.nu.NameUsageFieldLookup;
import life.catalogue.es.query.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NULL;

/**
 * Translates a single request parameter into an Elasticsearch query. If the parameter is multi-valued and contains no symbolic values (like
 * _NULL), a terms query will be generated; if it has a mixture of literal and symbolic values, an OR query will generated.
 */
class FilterTranslator {

  private final NameUsageSearchRequest request;

  FilterTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Query translate(NameUsageSearchParameter param) throws InvalidQueryException {
    List<Query> queries = new ArrayList<>();
    String[] fields = NameUsageFieldLookup.INSTANCE.lookup(param);
    for (String field : fields) {
      if (containsNullValue(param)) {
        queries.add(new IsNullQuery(field));
      }
      if (containsNotNullValue(param)) {
        queries.add(new IsNotNullQuery(field));
      }
      List<?> paramValues = getLiteralValues(param);
      if (paramValues.size() == 1) {
        queries.add(new TermQuery(field, paramValues.get(0)));
      } else if (paramValues.size() > 1) {
        queries.add(new TermsQuery(field, paramValues));
      }
      if (queries.size() == 1) {
        return queries.get(0);
      }
    }
    BoolQuery query = new BoolQuery();
    queries.forEach(query::should);
    return query;
  }

  private List<?> getLiteralValues(NameUsageSearchParameter param) throws InvalidQueryException {
    return request.getFilterValues(param).stream().filter(this::isLiteral).collect(toList());
  }

  private boolean isLiteral(Object o) {
    return !o.equals(IS_NULL) && !o.equals(IS_NOT_NULL);
  }

  // Is one of the values of the query parameter the symbol for IS NULL?
  private boolean containsNullValue(NameUsageSearchParameter param) {
    return request.getFilterValues(param).contains(IS_NULL);
  }

  // Is one of the values of the query parameter the symbol for IS NOT NULL?
  private boolean containsNotNullValue(NameUsageSearchParameter param) {
    return request.getFilterValues(param).contains(IS_NOT_NULL);
  }
}
