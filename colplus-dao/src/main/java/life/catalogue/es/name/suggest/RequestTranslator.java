package life.catalogue.es.name.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.RangeQuery;
import life.catalogue.es.query.TermQuery;

import static life.catalogue.es.query.SortField.SCORE;
import static org.gbif.nameparser.api.Rank.SPECIES;

/**
 * Translates the {@code NameSuggestRequest} into a native Elasticsearch search request.
 */
class RequestTranslator {

  private final NameUsageSuggestRequest request;

  RequestTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  EsSearchRequest translate() {
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", request.getDatasetKey()))
        .filter(new RangeQuery<Integer>("rank").greaterOrEqual(SPECIES.ordinal()))
        .must(new QTranslator(request).translate());
    return new EsSearchRequest()
        /*.select("usageId", "scientificName", "acceptedName", "vernacularNames", "rank", "nomCode")*/
        .where(query)
        .sortBy(SCORE)
        .size(request.getLimit());
  }

}
