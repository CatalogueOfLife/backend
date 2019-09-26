package org.col.es.name.search;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.BoolQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.MatchAllQuery;
import org.col.es.query.Query;

/**
 * Translates a NameSearchRequest into a native Elasticsearch search request.
 */
class RequestTranslator {

  static Query generateQuery(NameSearchRequest request) {
    if (request.getFilters().isEmpty()) {
      if (StringUtils.isEmpty(request.getQ())) {
        return new MatchAllQuery();
      }
      return new QTranslator(request).translate();
    } else if (StringUtils.isEmpty(request.getQ())) {
      return new FiltersTranslator(request).translate();
    }
    return new BoolQuery()
        .filter(new FiltersTranslator(request).translate())
        .filter(new QTranslator(request).translate());
  }

  private final NameSearchRequest request;
  private final Page page;

  RequestTranslator(NameSearchRequest request, Page page) {
    this.request = request;
    this.page = page;
  }

  EsSearchRequest translate() {
    EsSearchRequest es = new EsSearchRequest();
    es.setFrom(page.getOffset());
    es.setSize(page.getLimit());
    es.setQuery(generateQuery(request));
    es.setSort(new SortByTranslator(request).translate());
    if (!request.getFacets().isEmpty()) {
      FacetsTranslator ft = new FacetsTranslator(request);
      es.setAggregations(ft.translate());
    }
    return es;
  }

}
