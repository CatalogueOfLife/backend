package org.col.es.translate;

import java.util.LinkedHashMap;
import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.Aggregation;
import org.col.es.query.Facet;
import org.col.es.query.Query;

class FacetsTranslator {

  private final NameSearchRequest request;

  FacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Map<String, Aggregation> translate() throws InvalidQueryException {
    if (request.getFacets() == null) {
      return null;
    }
    Map<String, Aggregation> aggs = new LinkedHashMap<>();
    for (NameSearchParameter facet : request.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest copy = request.copy();
      copy.removeFilter(facet);
      Query filter = NameSearchRequestTranslator.generateQuery(copy);
      aggs.put(field, new Facet(field, filter));
    }
    return aggs;
  }

}
