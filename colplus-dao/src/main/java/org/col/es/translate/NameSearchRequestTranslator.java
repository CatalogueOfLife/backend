package org.col.es.translate;

import java.util.Optional;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.Query;
import org.col.es.query.SortBuilder;

/**
 * Translates a CoL NameSearchRequest into an actual Elasticsearch query. Main class of this
 * package.
 *
 */
public class NameSearchRequestTranslator {

  private final NameSearchRequest request;
  private final Page page;

  public NameSearchRequestTranslator(NameSearchRequest request, Page page) {
    this.request = request;
    this.page = page;
  }

  public EsSearchRequest translate() throws InvalidQueryException {
    EsSearchRequest req = new EsSearchRequest();
    req.setFrom(page.getOffset());
    req.setSize(page.getLimit());
    if (request.getSortBy() == null) {
      req.setSortBuilder(SortBuilder.DEFAULT_SORT);
    } else {
      req.setSortBuilder(new SortBuilder(request.getSortBy()));
    }
    Optional<Query> q1 = new NameSearchParametersTranslator(request).translate();
    Optional<Query> q2 = new QTranslator(request).translate();
    Query query = null;
    if (q1.isPresent()) {
      if (q2.isPresent()) {
        query = new BoolQuery().must(q1.get()).must(q2.get());
      } else {
        query = q1.get();
      }
    } else if (q2.isPresent()) {
      query = q2.get();
    }
    if (query != null) {
      if (request.getSortBy() != NameSearchRequest.SortBy.RELEVANCE) {
        // Cardinality of names and keys is so high it doesn't make sense to let ES waste time on
        // calculating scores.
        query = new ConstantScoreQuery(query);
      }
      req.setQuery(query);
    }
    return req;
  }

}
