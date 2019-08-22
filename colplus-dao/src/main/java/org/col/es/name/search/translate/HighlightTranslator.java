package org.col.es.name.search.translate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.es.query.Highlight;

import static org.col.common.collection.CollectionUtils.isEmpty;

/**
 * Translates the values in the NameSearchRequest.content field into a "highlight" object within the Elasticsearch search request.
 */
/*
 * N.B. not used currently. See org.col.es.Highlight class.
 */
public class HighlightTranslator {

  private final NameSearchRequest request;

  HighlightTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Highlight translate() {
    // See above
    if (true) {
      return null;
    }
    @SuppressWarnings("unused")
    Set<SearchContent> content = request.getContent();
    List<String> fields = new ArrayList<>(3);
    if (isEmpty(content)) {
      content = EnumSet.allOf(SearchContent.class);
    }
    content.stream().forEach(sc -> {
      switch (sc) {
        case AUTHORSHIP:
          fields.add("authorship");
          break;
        case SCIENTIFIC_NAME:
          fields.add("scientificName");
          break;
        case VERNACULAR_NAME:
          fields.add("vernacularNames");
        default:
          break;
      }
    });
    Highlight highlight = Highlight.forFields(fields);
    highlight.setNumberOfFragments(0);
    return highlight;
  }

}
