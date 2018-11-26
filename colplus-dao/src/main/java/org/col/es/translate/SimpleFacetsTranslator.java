package org.col.es.translate;

import java.util.LinkedHashMap;
import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.FacetAggregation;
import org.col.es.query.MatchAllQuery;

import static org.col.es.translate.AggregationLabelProvider.getFacetLabel;

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
      aggs.put(getFacetLabel(facet), new FacetAggregation(field, MatchAllQuery.INSTANCE));
      /*
       * We could use a simple TermsAggregation here, but then we would have to associate the facet label with the terms aggregation, whereas
       * ordinarily the "BUCKETS" label is associated with the TermsAggregation while the facet label is associated with the FacetAggregation
       * containing the TermsAggregation. In other words the facet label makes an awkward level jump that would make the query response
       * harder to parse.
       */
      // aggs.put(getFacetLabel(facet), new TermsAggregation(field));
    }
    return aggs;
  }

}
