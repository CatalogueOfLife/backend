package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.es.dsl.EsSearchRequest;

public class NameSuggestRequestTranslator {

  private final NameSuggestRequest request;

  public NameSuggestRequestTranslator(NameSuggestRequest request) {
    this.request = request;
  }

  public EsSearchRequest translate() {
    EsSearchRequest searchRequest = EsSearchRequest.emptyRequest().select("usageId", "scientificName", "acceptedName", "rank", "nomCode");
    searchRequest.setSize(request.getLimit());
    QTranslator translator = new QTranslator(request);
    searchRequest.setQuery(translator.translate());

    return null;
  }

}
