package life.catalogue.es2.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es2.query.FiltersTranslator;
import life.catalogue.es2.query.QTranslator;
import life.catalogue.es2.query.SortByTranslator;

import java.util.Map;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;

/**
 * Translates a {@link NameUsageSearchRequest} into a native Elasticsearch search request.
 */
class RequestTranslator {

  static Query generateQuery(NameUsageSearchRequest request) {
    if (FiltersTranslator.mustGenerateFilters(request)) {
      if (request.hasQ()) {
        Query filterQuery = new FiltersTranslator(request).translate();
        Query qQuery = new QTranslator(request).translate();
        return Query.of(q -> q.bool(b -> b.filter(filterQuery).must(qQuery)));
      }
      return new FiltersTranslator(request).translate();
    } else if (request.hasQ()) {
      return new QTranslator(request).translate();
    }
    return Query.of(q -> q.matchAll(m -> m));
  }

  private final NameUsageSearchRequest request;
  private final Page page;

  RequestTranslator(NameUsageSearchRequest request, Page page) {
    this.request = request;
    this.page = page;
  }

  /**
   * Translates the NameUsageSearchRequest into a real Elasticsearch search request.
   */
  SearchRequest translateRequest(String index) {
    Query query = generateQuery(request);
    var sortOptions = new SortByTranslator(request).translate();

    return SearchRequest.of(s -> {
      s.index(index)
       .from(page.getOffset())
       .size(page.getLimit())
       .query(query)
       .sort(sortOptions)
       .trackTotalHits(th -> th.enabled(true));

      if (!request.getFacets().isEmpty()) {
        FacetsTranslator ft = new FacetsTranslator(request);
        Map<String, Aggregation> aggs = ft.translate();
        s.aggregations(aggs);
      }
      return s;
    });
  }

}
