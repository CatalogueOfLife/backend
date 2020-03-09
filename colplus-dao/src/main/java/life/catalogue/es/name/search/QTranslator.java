package life.catalogue.es.name.search;

import java.util.ArrayList;
import java.util.List;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.name.QMatcher;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.AUTHORSHIP;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.SCIENTIFIC_NAME;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.VERNACULAR_NAME;

class QTranslator {

  /**
   * The extra boost to give to scientific name matches (versus vernacular name or authorship matches), reflecting the fact that, all being
   * equal, we'd like to see them higher up in the list of matches.
   */
  public static final Double SCINAME_EXTRA_BOOST = 10.0;

  private final NameUsageSearchRequest request;

  QTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Query translate() {
    QMatcher qMatcher = QMatcher.getInstance(request);
    List<Query> queries = new ArrayList<Query>(request.getContent().size());
    if (request.getContent().contains(SCIENTIFIC_NAME)) {
      queries.add(qMatcher.getScientificNameQuery().withBoost(SCINAME_EXTRA_BOOST));
    }
    if (request.getContent().contains(VERNACULAR_NAME)) {
      queries.add(qMatcher.getVernacularNameQuery());
    }
    if (request.getContent().contains(AUTHORSHIP)) {
      queries.add(qMatcher.getAuthorshipQuery());
    }
    if (queries.size() == 1) {
      return queries.get(0);
    }
    BoolQuery query = new BoolQuery();
    queries.forEach(query::should);
    return query;
  }

}
