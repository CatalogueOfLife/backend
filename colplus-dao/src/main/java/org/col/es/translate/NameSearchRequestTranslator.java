package org.col.es.translate;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.Query;

/**
 * Translates a CoL NameSearchRequest into an actual Elasticsearch query. Main class of this package.
 *
 */
public class NameSearchRequestTranslator {

  static Query generateQuery(NameSearchRequest request) throws InvalidQueryException {
    Query query1 = new NameSearchParamsTranslator(request).translate();
    Query query2 = new QTranslator(request).translate();
    Query query = null;
    if (query1 != null) {
      if (query2 != null) {
        query = new BoolQuery().filter(query1).filter(query2);
      } else {
        query = query1;
      }
    } else if (query2 != null) {
      query = query2;
    }
    if (query != null) {
      query = new ConstantScoreQuery(query);
    }
    return query;
  }

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
    es.setQuery(generateQuery(request));
    es.setSort(new SortByTranslator(request).translate());
    return es;
  }

}
