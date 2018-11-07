package org.col.es.translate;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Strings;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
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
    if (Strings.isNullOrEmpty(request.getQ())) {
      return Optional.empty();
    }
    Set<SearchContent> content;
    if (request.getContent() == null || request.getContent().isEmpty()) {
      content = EnumSet.allOf(SearchContent.class);
    } else {
      content = request.getContent();
    }
    if (content.size() == 1) {
      return request.getContent().stream().map(this::translate).findFirst();
    }
    BoolQuery boolQuery = new BoolQuery();
    for (SearchContent sc : content) {
      boolQuery.should(translate(sc));
    }
    return Optional.of(boolQuery);
  }

  private Query translate(SearchContent sc) {
    switch (sc) {
      case AUTHORSHIP:
        return new AutoCompleteQuery("authorship", request.getQ());
      case SCIENTIFIC_NAME:
        return new AutoCompleteQuery("scientificName", request.getQ());
      case VERNACULAR_NAME:
      default:
        return new AutoCompleteQuery("vernacularNames", request.getQ());
    }
  }

}
