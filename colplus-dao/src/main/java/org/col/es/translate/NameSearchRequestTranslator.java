package org.col.es.translate;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.BoolQuery;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.Query;

import static org.col.common.util.CollectionUtils.notEmpty;

/**
 * Translates a CoL NameSearchRequest into an actual Elasticsearch query. Main class of this package.
 *
 */
public class NameSearchRequestTranslator {

  /**
   * Generates an ES query from the specified NameSearchRequest. Generally there is no reason to calculate relevance scores, so the 2nd
   * argument should be true. However, in some cases the query already takes place in a non-scoring context, or you know in advance there
   * won't be any documents to calculate scores for. In these cases not wrapping the basic query into a ConstantScoreQuery makes the
   * resulting JSON easier to read.
   */
  static Query generateQuery(NameSearchRequest request, boolean constantScore) {
    Query query1 = new FiltersTranslator(request).translate();
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
    if (query != null && constantScore) {
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

  public EsSearchRequest translate() {
    EsSearchRequest es = new EsSearchRequest();
    es.setFrom(page.getOffset());
    es.setSize(page.getLimit());
    es.setQuery(generateQuery(request, true));
    es.setSort(new SortByTranslator(request).translate());
    if(notEmpty(request.getFacets())) {
      FacetsTranslator ft = new FacetsTranslatorFactory(request).createTranslator();
      es.setAggregations(ft.translate());
    }
    return es;
  }

}
