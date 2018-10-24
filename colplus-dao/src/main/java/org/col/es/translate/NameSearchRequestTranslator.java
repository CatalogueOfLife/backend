package org.col.es.translate;

import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortBuilder;

/**
 * Translates a CoL NameSearchRequest into an actual Elasticsearch query. Main class of this
 * package.
 *
 */
public class NameSearchRequestTranslator {

  private final NameSearchRequest request;

  public NameSearchRequestTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public EsSearchRequest translate() throws InvalidQueryException {
    EsSearchRequest req = new EsSearchRequest();
    req.setFrom(request.getOffset());
    req.setSize(request.getLimit());
    BoolQuery mainQuery = new BoolQuery();
    if (request.getSortBy() == null) {
      req.setSortBuilder(SortBuilder.DEFAULT_SORT);
    } else {
      req.setSortBuilder(new SortBuilder(request.getSortBy()));
    }
    new NameSearchParametersTranslator(request).translate().ifPresent(mainQuery::must);
    new QTranslator(request).translate().ifPresent(mainQuery::must);
    if (!mainQuery.isEmpty()) {
      if (request.getSortBy() == NameSearchRequest.SortBy.RELEVANCE) {
        req.setQuery(mainQuery);
      } else {
        /*
         * Names and keys have such high cardinality that it doesn't make sense to let ES waste time
         * on calculating scores.
         */
        req.setQuery(new ConstantScoreQuery(mainQuery));
      }
    }
    return req;
  }

}
