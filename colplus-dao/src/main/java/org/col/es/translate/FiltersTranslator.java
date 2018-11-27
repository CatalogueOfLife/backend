package org.col.es.translate;

import java.util.Set;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.Query;

import static org.col.common.util.CollectionUtils.isEmpty;

/**
 * Translates all query parameters except the "q" parameter into an Elasticsearch query. Unless there is just one query parameter, this will
 * result in an AND query. For example: ?rank=genus&nom_status=available is translated into (rank=genus AND nom_status=available). If a
 * request parameter is multi-valued, this will result in a nested OR query. For example: ?nom_status=available&rank=family&rank=genus. This
 * will be translated into (nom_status=available AND (rank=family OR rank=genus)). Finally, a query parameter may be present but have no
 * value, for example: ?nom_status=&rank=. This will be translated into an IS NULL constraint.
 *
 */
class FiltersTranslator {

  private final NameSearchRequest request;

  FiltersTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Query translate() throws InvalidQueryException {
    if (isEmpty(request.getFilters())) {
      return null;
    }
    FilterTranslator ft = new FilterTranslator(request);
    Set<NameSearchParameter> params = request.getFilters().keySet();
    if (params.size() == 1) {
      return ft.translate(params.iterator().next());
    }
    BoolQuery boolQuery = new BoolQuery();
    for (NameSearchParameter param : params) {
      boolQuery.filter(ft.translate(param));
    }
    return boolQuery;
  }

}
