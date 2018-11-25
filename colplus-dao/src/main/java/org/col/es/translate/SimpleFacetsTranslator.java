package org.col.es.translate;

import java.util.LinkedHashMap;
import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.TermsAggregation;

/**
 * A simple facets translator that operates within the current execution context.
 */
class SimpleFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  SimpleFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  @Override
  public Map<String, Aggregation> translate() {
    Map<String, Aggregation> aggs = new LinkedHashMap<>(request.getFacets().size());
    for (NameSearchParameter facet : request.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      aggs.put(field, new TermsAggregation(field));
    }
    return aggs;
  }

}
