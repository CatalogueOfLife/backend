package org.col.es.name.search;

import java.util.ArrayList;
import java.util.List;

import org.col.api.search.NameSearchRequest;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.Query;
import org.col.es.model.NameStrings;

import static org.col.api.search.NameSearchRequest.SearchContent.AUTHORSHIP;
import static org.col.api.search.NameSearchRequest.SearchContent.SCIENTIFIC_NAME;
import static org.col.api.search.NameSearchRequest.SearchContent.VERNACULAR_NAME;
import static org.col.es.name.QTranslationUtils.getAuthorshipQuery;
import static org.col.es.name.QTranslationUtils.getScientificNameQuery;
import static org.col.es.name.QTranslationUtils.getVernacularNameQuery;

class QTranslator {

  private final NameSearchRequest request;
  private final NameStrings strings;

  QTranslator(NameSearchRequest request) {
    this.request = request;
    this.strings = new NameStrings(request.getQ());
  }

  Query translate() {
    List<Query> queries = new ArrayList<Query>(request.getContent().size());
    if (request.getContent().contains(SCIENTIFIC_NAME)) {
      queries.add(getScientificNameQuery(request.getQ(), strings));
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
