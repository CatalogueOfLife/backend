package life.catalogue.es.search;

import life.catalogue.api.search.NameUsageSearchRequest;

/**
 * Translates the values in the NameSearchRequest.content field into a "highlight" object within the Elasticsearch search request.
 * <p>
 * Not used currently. Highlighting is done client-side by {@link NameUsageHighlighter}.
 */
class HighlightTranslator {

  private final NameUsageSearchRequest request;

  HighlightTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

}
