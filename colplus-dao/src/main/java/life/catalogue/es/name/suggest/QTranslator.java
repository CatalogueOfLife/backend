package life.catalogue.es.name.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.name.QTranslationHelper;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;

class QTranslator {

  static final String SN_QUERY_NAME = "sn";
  static final String VN_QUERY_NAME = "vn";

  private final NameUsageSuggestRequest request;

  QTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  Query translate() {
    QTranslationHelper helper = new QTranslationHelper(request);
    if (request.suggestVernaculars()) {
      return new BoolQuery()
          .should(helper.getScientificNameQuery().withName(SN_QUERY_NAME))
          .should(helper.getVernacularNameQuery().withName(VN_QUERY_NAME));
    }
    return helper.getScientificNameQuery().withName(SN_QUERY_NAME);
  }

}
