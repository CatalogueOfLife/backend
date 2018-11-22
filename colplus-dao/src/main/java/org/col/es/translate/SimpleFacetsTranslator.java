package org.col.es.translate;

import java.util.LinkedHashMap;
import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.TermsAggregation;

/**
 * Edge case. There is just one facet and no filters besides possibly one for the facet itself (i.e. the user has selected one or more
 * values from this facet). We don't need a separate execution context to guarantee we will retrieve all distinct values for the facet
 * (pagination considerations aside).
 */
class SimpleFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  SimpleFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  @Override
  public Map<String, Aggregation> translate() {
    Map<String, Aggregation> aggs = new LinkedHashMap<>();
    for (NameSearchParameter facet : request.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      aggs.put(getFacetLabel(field), new TermsAggregation(field));
    }
    return aggs;
  }

}
