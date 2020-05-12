package life.catalogue.es.nu.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.DownwardConverter;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.TermQuery;
import static life.catalogue.es.query.SortField.SCORE;

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
    return new EsSearchRequest().where(query).sortBy(SCORE).size(request.getLimit());
  }

}
