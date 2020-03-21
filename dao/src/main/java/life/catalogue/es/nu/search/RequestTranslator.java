package life.catalogue.es.nu.search;

import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.*;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.MatchAllQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.TermQuery;

/**
 * Translates a {@link NameUsageSearchRequest} into a native Elasticsearch search request.
 */
class RequestTranslator {

  static Query generateQuery(NameUsageSearchRequest request) {
    String usageId = request.getFilterValue(USAGE_ID);
    if (usageId != null) {
      String datasetKey = request.getFilterValue(DATASET_KEY);
      return new BoolQuery()
          .filter(new TermQuery("usageId", usageId))
          .filter(new TermQuery("datasetKey", datasetKey));
    } else if (mustGenerateFilters(request)) {
      if (request.hasQ()) {
        return new BoolQuery()
            .filter(new FiltersTranslator(request).translate())
            .filter(new QTranslator(request).translate());
      }
      return new FiltersTranslator(request).translate();
    } else if (request.hasQ()) {
      return new QTranslator(request).translate();
    }
    return new MatchAllQuery();
  }

  private final NameUsageSearchRequest request;
  private final Page page;

  RequestTranslator(NameUsageSearchRequest request, Page page) {
    this.request = request;
    this.page = page;
  }

  /**
   * Translates the NameUsageSearchRequest into a real Elasticsearch search request.
   * 
   * @return
   */
  EsSearchRequest translateRequest() {
    EsSearchRequest es = new EsSearchRequest();
    es.setFrom(page.getOffset());
    es.setSize(page.getLimit());
    es.setQuery(generateQuery(request));
    es.setSort(new SortByTranslator(request).translate());
    // Unless explicitly specified otherwise, set to true:
    if (es.getTrackTotalHits() == null) {
      es.setTrackTotalHits(Boolean.TRUE);
    }
    if (!request.getFacets().isEmpty()) {
      FacetsTranslator ft = new FacetsTranslator(request);
      es.setAggregations(ft.translate());
    }
    return es;
  }

  private static boolean mustGenerateFilters(NameUsageSearchRequest request) {
    return request.getFilters().size() > 1 || (request.getFilters().size() == 1 && !request.getFilters().keySet().contains(CATALOGUE_KEY));
  }

}
