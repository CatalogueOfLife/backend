package life.catalogue.es.nu.search;

import java.util.Map;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.nu.NameUsageFieldLookup;
import life.catalogue.es.query.Aggregation;
import life.catalogue.es.query.FacetAggregation;
import life.catalogue.es.query.FilterAggregation;
import life.catalogue.es.query.GlobalAggregation;
import life.catalogue.es.query.Query;
import static java.util.Collections.singletonMap;
import static life.catalogue.es.nu.search.RequestTranslator.generateQuery;

/**
 * Translates the facets in the NameSearchRequest object into a set of suitable aggregations. There is one edge case here, namely if there
 * is just one facet, with or without a corresponding filter (actually with or without any filter at all). In this case the aggregation can
 * take place within the current execution context (the document set produced by the main query). However we wilfully incur some performance
 * overhead by still making it take place within a separate execution context with exactly the same query as the main query. This allows for
 * huge streamlining of the code because the response from Elasticsearch will now always look the same. Besides, it's an unlikely scenario
 * (there will probably always be more than one facet). And also, since everything takes place in a filter context, Elasticsearch will
 * probably cache the filter, thus reducing the performance overhead.
 */
public class FacetsTranslator {

  public static final String GLOBAL_AGG_LABEL = "_global_";
  public static final String FILTER_AGG_LABEL = "_filter_";
  public static final String FACET_AGG_LABEL = "_values_";
  
  private final NameUsageSearchRequest request;

  FacetsTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  Map<String, Aggregation> translate() {
    NameUsageSearchRequest copy = request.copy();
    copy.getFilters().keySet().retainAll(request.getFacets());
    copy.setQ(null);
    GlobalAggregation globalAgg = new GlobalAggregation();
    FilterAggregation filterAgg = new FilterAggregation(getContextFilter());
    globalAgg.nest(FILTER_AGG_LABEL, filterAgg);
    for (NameUsageSearchParameter facet : copy.getFacets()) {
      String field = NameUsageFieldLookup.INSTANCE.lookup(facet);
      // Temporarily remove the filter corresponding to the facet (if any), otherwise the values retrieved for the facet would collapse to
      // those specified by the filter.
      NameUsageSearchRequest temp = copy.copy();
      temp.removeFilter(facet);
      Aggregation agg = new FacetAggregation(field, generateQuery(temp));
      filterAgg.nest(facet.name(), agg);
    }
    return singletonMap(GLOBAL_AGG_LABEL, globalAgg);
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
