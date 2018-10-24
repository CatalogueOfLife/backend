package org.col.es.translate;

import java.util.Optional;

import com.google.common.base.Strings;

import org.col.api.search.NameSearchRequest;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.Query;

/*
 * Translates the "q" request parameter into an Elasticsearch query.
 */
class QTranslator {

  private final NameSearchRequest request;

  QTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Optional<Query> translate() {
    if (!Strings.isNullOrEmpty(request.getQ())) {
      return Optional.empty();
    }
    Query query;
    if (request.getContent().size() == 1) {
      query = translate(request.getContent().iterator().next());
    } else {
      query = request.getContent().stream().map(this::translate).collect(BoolQuery::new,
          BoolQuery::should, BoolQuery::should);
    }
    return Optional.of(query);
  }

  private Query translate(NameSearchRequest.SearchContent sc) {
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
