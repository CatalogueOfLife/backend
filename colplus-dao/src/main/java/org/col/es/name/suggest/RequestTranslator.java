package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.es.dsl.EsSearchRequest;

/**
 * Translates the {@code NameSuggestRequest} into a real Elasticsearch search request.
 */
class RequestTranslator {

  private static final String[] fields = {"usageId", "scientificName", "acceptedName", "rank", "nomCode"};

  private final NameSuggestRequest request;
  private final QTranslator qTranslator;

  RequestTranslator(NameSuggestRequest request) {
    this.request = request;
    this.qTranslator = new QTranslator(request);
  }

  EsSearchRequest getScientificNameQuery() {
    EsSearchRequest searchRequest = EsSearchRequest.emptyRequest()
        .select(fields)
        .where(qTranslator.getScientificNameQuery())
        .size(request.getLimit());
    return searchRequest;
  }

  EsSearchRequest getVernacularNameQuery() {
    EsSearchRequest searchRequest = EsSearchRequest.emptyRequest()
        .select(fields)
        .where(qTranslator.getVernacularNameQuery())
        .size(request.getLimit());
    return searchRequest;
  }

}
