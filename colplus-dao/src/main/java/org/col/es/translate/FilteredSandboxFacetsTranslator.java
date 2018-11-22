package org.col.es.translate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.Aggregation;
import org.col.es.query.FacetAggregation;
import org.col.es.query.Query;
import org.col.es.query.TermsAggregation;

class FacetsTranslator {

  private final NameSearchRequest request;

  FacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Map<String, Aggregation> translate() throws InvalidQueryException {
    if (request.getFacets().size() == 1) {
      String field = EsFieldLookup.INSTANCE.lookup(request.getFacets().iterator().next());
      return Collections.singletonMap(field, new TermsAggregation(field));
    }
    Map<String, Aggregation> aggs = new LinkedHashMap<>();
    /*
     * For each facet remove the corresponding filter (if any) because thay would collapse the facet to the values of the filter. Then
     * constrain the document set over which to aggregate using the values of the other facets.
     */
    for (NameSearchParameter facet : request.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest copy = request.copy();
      copy.removeFilter(facet);
      Query filter = NameSearchRequestTranslator.generateQuery(copy, false);
      aggs.put(field.toUpperCase() + "_FACET", new FacetAggregation(field, filter));
    }
    return aggs;
  }

}
