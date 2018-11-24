package org.col.es.translate;

import java.util.Collections;
import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.TermsAggregation;

/**
 * A simple facets translator that operates within the current execution context. Produced by the FacetsTranslator if there is just one
 * facet.
 */
class SimpleFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  SimpleFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  @Override
  public Map<String, Aggregation> translate() {
    NameSearchParameter facet = request.getFacets().iterator().next();
    String field = EsFieldLookup.INSTANCE.lookup(facet);
    return Collections.singletonMap(field, new TermsAggregation(field));
  }

}
