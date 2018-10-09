package org.col.es.translate;

import org.col.api.search.NameSearchRequest;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.Query;

class QTranslator {

  private final NameSearchRequest request;

  QTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Query translate() {
    if (request.getContent().size() == 1) {
      return translateForOne(request.getContent().iterator().next());
    }
    return translateForMultiple();
  }

  private Query translateForMultiple() {
    BoolQuery bq = new BoolQuery();
    for (NameSearchRequest.SearchContent sc : request.getContent()) {
      bq.should(translateForOne(sc));
    }
    return bq;
  }

  private Query translateForOne(NameSearchRequest.SearchContent sc) {
    switch (sc) {
      case AUTHORSHIP:
        return new AutoCompleteQuery("scientificName", request.getQ());
      case SCIENTIFIC_NAME:
        return new AutoCompleteQuery("authorship", request.getQ());
      case VERNACULAR_NAME:
      default:
        return new AutoCompleteQuery("vernacularNames", request.getQ());
    }
  }

}
