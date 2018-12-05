package org.col.es.translate;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.BoolQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.MatchAllQuery;
import org.col.es.query.Query;

import static org.col.common.util.CollectionUtils.isEmpty;
import static org.col.common.util.CollectionUtils.notEmpty;

/**
 * Translates a CoL NameSearchRequest into an actual Elasticsearch query. Main class of this package.
 */
public class NameSearchRequestTranslator {

  static Query generateQuery(NameSearchRequest request) {
    if (isEmpty(request.getFilters())) {
      if (StringUtils.isEmpty(request.getQ())) {
        return MatchAllQuery.INSTANCE;
      }
      return new QTranslator(request).translate();
    } else if (StringUtils.isEmpty(request.getQ())) {
      return new FiltersTranslator(request).translate();
    }
    Query filters = new FiltersTranslator(request).translate();
    Query q = new QTranslator(request).translate();
    return new BoolQuery().filter(filters).filter(q);
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
    es.setQuery(generateQuery(request));
    es.setSort(new SortByTranslator(request).translate());
    if (notEmpty(request.getFacets())) {
      FacetsTranslator ft = new FacetsTranslator(request);
      es.setAggregations(ft.translate());
    }
    return es;
  }

}
