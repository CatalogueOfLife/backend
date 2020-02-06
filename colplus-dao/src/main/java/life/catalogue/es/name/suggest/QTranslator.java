package life.catalogue.es.name.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;

import static life.catalogue.es.name.QTranslationUtils.getScientificNameQuery;
import static life.catalogue.es.name.QTranslationUtils.getVernacularNameQuery;

class QTranslator {

  static final String SN_QUERY_NAME = "sn";
  static final String VN_QUERY_NAME = "vn";

  private final NameUsageSuggestRequest request;

  QTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  Query translate() {
    if (request.suggestVernaculars()) {
      return new BoolQuery()
          .should(getScientificNameQuery(request.getQ(), request.getSearchTerms()).withName(SN_QUERY_NAME))
          .should(getVernacularNameQuery(request.getQ()).withName(VN_QUERY_NAME));
    }
    return getScientificNameQuery(request.getQ(), request.getSearchTerms()).withName(SN_QUERY_NAME);
  }

}
