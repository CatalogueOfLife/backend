package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.api.search.NameSuggestion;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.EsNameUsageResponse;
import org.col.es.name.NameUsageDocumentConverter;
import org.col.es.response.SearchHit;

class NameSuggestResponseConverter extends NameUsageDocumentConverter {

  private final NameSuggestRequest request;

  NameSuggestResponseConverter(NameSuggestRequest request, EsNameUsageResponse response) {
    super(response);
    this.request = request;
  }

  NameSuggestResponse convert() {
    String q = request.getQ().trim().toLowerCase();
    return null;
  }

  private NameSuggestion extractSuggestion(SearchHit<NameUsageDocument> hit) {
    NameUsageDocument doc = hit.getSource();
    NameSuggestion ns = new NameSuggestion();
    ns.setAcceptedName(doc.getAcceptedName());
    return ns;
  }

}
