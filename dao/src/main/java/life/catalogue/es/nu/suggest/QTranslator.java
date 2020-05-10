package life.catalogue.es.nu.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.nu.QMatcher;
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
    QMatcher matcher = QMatcher.getInstance(request);
    if (request.suggestVernaculars()) {
      return new BoolQuery()
          .should(matcher.getScientificNameQuery().withName(SN_QUERY_NAME))
          .should(matcher.getVernacularNameQuery().withName(VN_QUERY_NAME));
    }
    return matcher.getScientificNameQuery().withName(SN_QUERY_NAME);
  }

}
