package life.catalogue.es.name.search;

import java.util.ArrayList;
import java.util.List;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;

import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.AUTHORSHIP;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.SCIENTIFIC_NAME;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.VERNACULAR_NAME;
import static life.catalogue.es.name.QTranslationUtils.getAuthorshipQuery;
import static life.catalogue.es.name.QTranslationUtils.getScientificNameQuery;
import static life.catalogue.es.name.QTranslationUtils.getVernacularNameQuery;

class QTranslator {

  private final NameUsageSearchRequest request;

  QTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Query translate() {
    List<Query> queries = new ArrayList<Query>(request.getContent().size());
    if (request.getContent().contains(SCIENTIFIC_NAME)) {
      queries.add(getScientificNameQuery(request.getQ()));
    }
    if (request.getContent().contains(VERNACULAR_NAME)) {
      queries.add(getVernacularNameQuery(request.getQ()));
    }
    if (request.getContent().contains(AUTHORSHIP)) {
      queries.add(getAuthorshipQuery(request.getQ()));
    }
    if (queries.size() == 1) {
      return queries.get(0);
    }
    BoolQuery query = new BoolQuery();
    queries.forEach(query::should);
    return query;
  }

}
