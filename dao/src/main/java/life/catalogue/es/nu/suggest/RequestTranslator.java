package life.catalogue.es.nu.suggest;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.DownwardConverter;
import life.catalogue.es.nu.NameUsageFieldLookup;
import life.catalogue.es.nu.SortByTranslator;
import life.catalogue.es.nu.search.FiltersTranslator;
import life.catalogue.es.query.*;

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
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", request.getDatasetKey()))
        .must(new QTranslator(request).translate());
    final String statusField = NameUsageFieldLookup.INSTANCE.lookupSingle(NameUsageSearchParameter.STATUS);
    // always avoid bare names
    query.filter(new IsNotNullQuery(statusField));
    if (request.isAccepted()) {
      query.filter(new TermsQuery(statusField, ACCEPTED.ordinal(), PROVISIONALLY_ACCEPTED.ordinal()));
    }
    FiltersTranslator.processMinMaxRank(request).forEach(query::filter);
    EsSearchRequest req = new EsSearchRequest()
      .where(query)
      .size(request.getLimit());
    req.setSort(new SortByTranslator(request).translate());
    return req;
  }

}
