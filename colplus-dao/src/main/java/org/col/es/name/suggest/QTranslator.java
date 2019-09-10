package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.Query;
import org.col.es.model.NameStrings;

import static org.col.es.name.QTranslationUtils.getScientificNameQuery;
import static org.col.es.name.QTranslationUtils.getVernacularNameQuery;

class QTranslator {

  static final String SN_QUERY_NAME = "sn";
  static final String VN_QUERY_NAME = "vn";

  private final NameSuggestRequest request;
  private final NameStrings strings;

  QTranslator(NameSuggestRequest request) {
    this.request = request;
    this.strings = new NameStrings(request.getQ());
  }

  Query translate() {
    Query snQuery = getScientificNameQuery(request.getQ(), strings).withName(SN_QUERY_NAME);
    if (request.isSuggestVernaculars()) {
      Query vnQuery = getVernacularNameQuery(request.getQ()).withName(VN_QUERY_NAME);
      return new BoolQuery().should(snQuery).should(vnQuery);
    }
    return snQuery;
  }

}
