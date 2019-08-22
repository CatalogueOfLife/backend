package org.col.es.name.suggest.translate;

import org.col.api.search.NameSuggestRequest;
import org.col.es.query.EsSearchRequest;

public class NameSuggestRequestTranslator {

  private final NameSuggestRequest request;

  public NameSuggestRequestTranslator(NameSuggestRequest request) {
    this.request = request;
  }

  public EsSearchRequest translate() {
    EsSearchRequest es = new EsSearchRequest();
    es.setSize(request.getLimit());
    return null;
  }

}
