package life.catalogue.es.nu.suggest;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.DownwardConverter;
import life.catalogue.es.nu.FilterTranslator;
import life.catalogue.es.nu.FiltersTranslator;
import life.catalogue.es.nu.NameUsageFieldLookup;
import life.catalogue.es.nu.SortByTranslator;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.IsNotNullQuery;
import life.catalogue.es.query.Query;

import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;
import static life.catalogue.api.vocab.TaxonomicStatus.ACCEPTED;
import static life.catalogue.api.vocab.TaxonomicStatus.PROVISIONALLY_ACCEPTED;

/**
 * Translates the {@code NameSuggestRequest} into a native Elasticsearch search request.
 */
class RequestTranslator implements DownwardConverter<NameUsageSuggestRequest, EsSearchRequest> {

  private final NameUsageSuggestRequest request;

  RequestTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  EsSearchRequest translate() {
    Query query = generateQuery(request);
    EsSearchRequest req = new EsSearchRequest()
      .where(query)
      .size(request.getLimit());
    req.setSort(new SortByTranslator(request).translate());
    return req;
  }

  static Query generateQuery(NameUsageSuggestRequest request) {
    if (request.isAccepted()) {
      request.addFilter(NameUsageSearchParameter.STATUS, ACCEPTED);
      request.addFilter(NameUsageSearchParameter.STATUS, PROVISIONALLY_ACCEPTED);
    }

    BoolQuery q = new BoolQuery();
    // always avoid bare names
    final String statusField = NameUsageFieldLookup.INSTANCE.lookupSingle(NameUsageSearchParameter.STATUS);
    q.filter(new IsNotNullQuery(statusField));
    q.must(new QTranslator(request).translate());

    if (request.hasFilter(USAGE_ID)) {
      q.filter(new FilterTranslator(request).translate(DATASET_KEY));
      q.filter(new FilterTranslator(request).translate(USAGE_ID));
    } else if (FiltersTranslator.mustGenerateFilters(request)) {
      q.filter(new FiltersTranslator(request).translate());
    }
    return q;
  }
}
