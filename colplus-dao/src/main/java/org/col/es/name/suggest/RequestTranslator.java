package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.dsl.TermQuery;

/**
 * Translates the {@code NameSuggestRequest} into a real Elasticsearch search request.
 */
class RequestTranslator {

  private final NameSuggestRequest request;

  RequestTranslator(NameSuggestRequest request) {
    this.request = request;
  }

  EsSearchRequest translate() {
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", request.getDatasetKey()))
        .must(new QTranslator(request).translate());
    return new EsSearchRequest()
        .select("usageId", "scientificName", "acceptedName", "rank", "nomCode")
        .where(query)
        .size(request.getLimit());
  }

}
