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
 * Translates a CoL NameSearchRequest into an actual Elasticsearch query. Main class of this package.
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
    EsSearchRequest es = new EsSearchRequest();
    es.setFrom(page.getOffset());
    es.setSize(page.getLimit());
    es.setSortBuilder(SortBuilder.create(request.getSortBy()));
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
      es.setQuery(new ConstantScoreQuery(query));
    }
    return es;
  }

}
