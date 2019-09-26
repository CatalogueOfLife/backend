package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.es.query.BoolQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.RangeQuery;
import org.col.es.query.TermQuery;

import static org.gbif.api.vocabulary.Rank.SPECIES;

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
        .filter(new RangeQuery<Integer>("rank").greaterOrEqual(SPECIES.ordinal()))
        .must(new QTranslator(request).translate());
    return new EsSearchRequest()
        .select("usageId", "scientificName", "acceptedName", "rank", "nomCode")
        .where(query)
        .size(request.getLimit());
  }

}
