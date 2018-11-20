package org.col.es.translate;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
      Set<NameSearchParameter> otherFacets = new LinkedHashSet<>(request.getFacets());
      otherFacets.remove(facet);
    }
    return null;
  }

}
