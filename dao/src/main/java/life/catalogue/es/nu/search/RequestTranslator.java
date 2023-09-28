package life.catalogue.es.nu.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.DownwardConverter;
import life.catalogue.es.nu.FiltersTranslator;
import life.catalogue.es.nu.SortByTranslator;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.MatchAllQuery;
import life.catalogue.es.query.Query;

/**
 * Translates a {@link NameUsageSearchRequest} into a native Elasticsearch search request. Mostly manages the other translators in this
 * package.
 */
class RequestTranslator implements DownwardConverter<NameUsageSearchRequest, EsSearchRequest> {

  static Query generateQuery(NameUsageSearchRequest request) {
    if (FiltersTranslator.mustGenerateFilters(request)) {
      if (request.hasQ()) {
        return BoolQuery.withFilters(
            new FiltersTranslator(request).translate(),
            new QTranslator(request).translate());
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

}
