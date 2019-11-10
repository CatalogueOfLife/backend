package org.col.es.name.search;

import org.col.api.model.Page;
import org.col.api.search.NameUsageSearchRequest;
import org.col.es.query.BoolQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.MatchAllQuery;
import org.col.es.query.Query;
import org.col.es.query.TermQuery;

import static org.col.api.search.NameUsageSearchParameter.DATASET_KEY;
import static org.col.api.search.NameUsageSearchParameter.USAGE_ID;

/**
 * Translates a NameSearchRequest into a native Elasticsearch search request.
 */
class RequestTranslator {

  static Query generateQuery(NameUsageSearchRequest request) {
    Query query;
    String usageId = request.getFilterValue(USAGE_ID);
    if (usageId != null) {
      String datasetKey = request.getFilterValue(DATASET_KEY);
      query = new BoolQuery()
          .filter(new TermQuery("usageId", usageId))
          .filter(new TermQuery("datasetKey", datasetKey));
    } else if (request.hasFilters()) {
      if (request.hasQ()) {
        query = new BoolQuery()
            .filter(new FiltersTranslator(request).translate())
            .filter(new QTranslator(request).translate());
      } else {
        query = new FiltersTranslator(request).translate();
      }
    } else if (request.hasQ()) {
      query = new QTranslator(request).translate();
    } else {
      query = new MatchAllQuery();
    }
    return query;
  }

  private final NameUsageSearchRequest request;
  private final Page page;

  RequestTranslator(NameUsageSearchRequest request, Page page) {
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
