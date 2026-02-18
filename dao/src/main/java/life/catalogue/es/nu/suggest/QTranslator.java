package life.catalogue.es.nu.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.nu.QMatcher;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

/**
 * Translates the value of the "q" query parameter into an Elasticsearch (sub)query.
 */
class QTranslator {

  private final NameUsageSuggestRequest request;

  QTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  Query translate() {
    QMatcher matcher = QMatcher.getInstance(request);
    return matcher.getScientificNameQuery();
  }

}
