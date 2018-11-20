package org.col.es.translate;

import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;

class FacetsTranslator {

  private final NameSearchRequest request;

  FacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Map<String, Aggregation> translate() {
    for (NameSearchParameter facet : request.getFacets()) {
      NameSearchRequest copy = request.copy();
      copy.removeFilter(facet);
    }
    return null;
  }

}
