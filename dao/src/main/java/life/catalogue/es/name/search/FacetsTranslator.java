package life.catalogue.es.name.search;

import java.util.Map;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.name.NameUsageFieldLookup;
import life.catalogue.es.query.Aggregation;
import life.catalogue.es.query.FacetAggregation;
import life.catalogue.es.query.FilterAggregation;
import life.catalogue.es.query.GlobalAggregation;
import life.catalogue.es.query.Query;
import life.catalogue.es.response.Aggregations;
import life.catalogue.es.response.ContextFilterWrapper;
import static java.util.Collections.singletonMap;
import static life.catalogue.es.name.search.RequestTranslator.generateQuery;

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

  private final NameUsageSearchRequest request;

  FacetsTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Map<String, Aggregation> translate() {
    NameUsageSearchRequest copy = request.copy();
    copy.getFilters().keySet().retainAll(request.getFacets());
    copy.setQ(null);
    GlobalAggregation context = new GlobalAggregation();
    FilterAggregation ctxFilterAgg = new FilterAggregation(getContextFilter());
    context.setNestedAggregations(singletonMap(ContextFilterWrapper.LABEL, ctxFilterAgg));
    for (NameUsageSearchParameter facet : copy.getFacets()) {
      String field = NameUsageFieldLookup.INSTANCE.lookup(facet);
      // Temporarily remove the filter corresponding to the facet (if any), otherwise the values retrieved for the facet would collapse to
      // those specified by the filter.
      NameUsageSearchRequest temp = copy.copy();
      temp.removeFilter(facet);
      Aggregation agg = new FacetAggregation(field, generateQuery(temp));
      ctxFilterAgg.addNestedAggregation(facet.getFacetLabel(), agg);
    }
    return singletonMap(Aggregations.LABEL, context);
  }

  private Query getContextFilter() {
    if (request.getFilters().isEmpty()) { // might still have a Q
      return generateQuery(request);
    }
    NameUsageSearchRequest copy = request.copy();
    copy.getFilters().keySet().removeAll(request.getFacets());
    return generateQuery(copy);
  }

}
