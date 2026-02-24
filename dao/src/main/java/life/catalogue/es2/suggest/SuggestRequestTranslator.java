package life.catalogue.es2.suggest;

import co.elastic.clients.elasticsearch.core.search.SourceConfig;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es2.query.FiltersTranslator;
import life.catalogue.es2.query.QTranslator;
import life.catalogue.es2.query.SortByTranslator;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;

/**
 * Translates a {@link NameUsageSearchRequest} into a native Elasticsearch search request.
 */
class SuggestRequestTranslator {
  static final SourceConfig sourceConfig = new SourceConfig.Builder().filter(f -> f
    .includes("id","group", "classification", "usage.*")
  ).build();

  static Query generateQuery(NameUsageSuggestRequest request) {
    // q param is required for suggest requests and enforced by validator
    if (FiltersTranslator.mustGenerateFilters(request)) {
      Query filterQuery = new FiltersTranslator(request).translate();
      Query qQuery = new QSuggestTranslater(request).translate();
      return Query.of(q -> q.bool(b -> b.filter(filterQuery).must(qQuery)));
    }
    return new QTranslator(request).translate();
  }

  private final NameUsageSuggestRequest request;

  SuggestRequestTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  /**
   * Translates the NameUsageSearchRequest into a real Elasticsearch search request.
   */
  SearchRequest translateRequest(String index) {
    Query query = generateQuery(request);
    var sortOptions = new SortByTranslator(request).translate();

    return SearchRequest.of(s -> {
      s.index(index)
       .source(sourceConfig) // only return the subset we actually need
       .from(0)
       .size(request.getLimit())
       .query(query)
       .sort(sortOptions)
       .trackTotalHits(th -> th.enabled(false));
      return s;
    });
  }

}
