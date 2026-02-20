package life.catalogue.es.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.QMatcher;

import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.*;

class QTranslator {

  /**
   * The extra boost to give to scientific name matches (versus authorship matches), reflecting the fact that, all being
   * equal, we'd like to see them higher up in the list of matches.
   */
  public static final float SCINAME_EXTRA_BOOST = 10.0f;

  private final NameUsageSearchRequest request;

  QTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Query translate() {
    QMatcher matcher = QMatcher.getInstance(request);
    List<Query> queries = new ArrayList<>(request.getContent().size());
    if (request.getContent().contains(SCIENTIFIC_NAME)) {
      queries.add(QMatcher.withBoost(matcher.getScientificNameQuery(), SCINAME_EXTRA_BOOST));
    }
    if (request.getContent().contains(AUTHORSHIP)) {
      queries.add(matcher.getAuthorshipQuery());
    }
    if (request.getContent().contains(VERNACULAR_NAME)) {
      queries.add(matcher.getVernacularQuery());
    }
    if (queries.size() == 1) {
      return queries.get(0);
    }
    return Query.of(q -> q.bool(b -> {
      queries.forEach(b::should);
      return b;
    }));
  }

}
