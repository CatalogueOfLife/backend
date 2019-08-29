package org.col.es.name.search;

import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.dsl.Aggregation;
import org.col.es.dsl.FacetAggregation;
import org.col.es.dsl.FilterAggregation;
import org.col.es.dsl.GlobalAggregation;
import org.col.es.dsl.Query;
import org.col.es.name.EsFieldLookup;

import static java.util.Collections.singletonMap;

import static org.col.es.name.NameFacetLabels.getContextFilterLabel;
import static org.col.es.name.NameFacetLabels.getContextLabel;
import static org.col.es.name.NameFacetLabels.getFacetLabel;
import static org.col.es.name.search.NameSearchRequestTranslator.generateQuery;

/**
 * Translates the facets in the NameSearchRequest object into a set of suitable aggregations. There is one edge case here, namely if there
 * is just one facet, with or without a corresponding filter (actually with or without any filter at all). In this case the aggregation can
 * take place within the current execution context (the document set produced by the main query). However we wilfully incur some performance
 * overhead by still making it take place within a separate execution context with exactly the same query as the main query. This allows for
 * huge streamlining of the code because the response from Elasticsearch will now always look the same. Besides, it's an unlikely scenario
 * (there will probably alsway be more than one facet). And also, since everything takes place in a filter context, Elasticsearch will
 * probably cache the filter, thus reducing the performance overhead.
 */
class FacetsTranslator {

  private final NameSearchRequest request;

  FacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Map<String, Aggregation> translate() {
    NameSearchRequest copy = request.copy();
    copy.getFilters().keySet().retainAll(request.getFacets());
    copy.setQ(null);
    GlobalAggregation context = new GlobalAggregation();
    FilterAggregation ctxFilterAgg = new FilterAggregation(getContextFilter());
    context.setNestedAggregations(singletonMap(getContextFilterLabel(), ctxFilterAgg));
    for (NameSearchParameter facet : copy.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      // Temporarily remove the filter corresponding to the facet (if any), otherwise the values retrieved for the facet would collapse to
      // those specified by the filter.
      NameSearchRequest temp = copy.copy();
      temp.removeFilter(facet);
      Aggregation agg = new FacetAggregation(field, generateQuery(temp));
      ctxFilterAgg.addNestedAggregation(getFacetLabel(facet), agg);
    }
    return singletonMap(getContextLabel(), context);
  }

  private Query getContextFilter() {
    if (request.getFilters().isEmpty()) { // might still have a Q
      return generateQuery(request);
    }
    NameSearchRequest copy = request.copy();
    copy.getFilters().keySet().removeAll(request.getFacets());
    return generateQuery(copy);
  }

}
