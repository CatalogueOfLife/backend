package life.catalogue.es.name.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.name.QMatcher;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;

/**
 * Translates the value of the "q" query parameter into an Elasticsearch (sub)query.
 */
class QTranslator {

  static final String SN_QUERY_NAME = "sn";
  static final String VN_QUERY_NAME = "vn";

  private final NameUsageSuggestRequest request;

  QTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  Query translate() {
    QMatcher qMatcher = QMatcher.getInstance(request);
    if (request.suggestVernaculars()) {
      return new BoolQuery()
          .should(qMatcher.getScientificNameQuery().withName(SN_QUERY_NAME))
          .should(qMatcher.getVernacularNameQuery().withName(VN_QUERY_NAME));
    }
    return qMatcher.getScientificNameQuery().withName(SN_QUERY_NAME);
  }

}
