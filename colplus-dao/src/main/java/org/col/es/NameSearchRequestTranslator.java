package org.col.es;

import org.col.api.search.NameSearchRequest;
import org.col.es.query.SearchRequest;

public class NameSearchRequestTranslator {

  private final NameSearchRequest request;

  public NameSearchRequestTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public String getNameUsageQuery() {
    SearchRequest sr = new SearchRequest();
    for(NameSearchRequest.SearchContent searchIn : request.getContent()) {
      switch(searchIn) {
        case AUTHORSHIP:
          break;
        case SCIENTIFIC_NAME:
          break;
        case VERNACULAR_NAME:
          break;
      }
    }
    return null;
  }

}
