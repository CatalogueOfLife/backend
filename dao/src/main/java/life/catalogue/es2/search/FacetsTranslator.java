package life.catalogue.es2.search;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es2.query.FieldLookup;

import java.util.LinkedHashMap;
import java.util.Map;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static java.util.Collections.singletonMap;
import static life.catalogue.es2.search.SearchRequestTranslator.generateQuery;

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

    // Build per-facet aggregations
    Map<String, Aggregation> facetAggs = new LinkedHashMap<>();
    for (NameUsageSearchParameter facet : copy.getFacets()) {
      String field = FieldLookup.INSTANCE.lookupSingle(facet);
      // Temporarily remove the filter corresponding to the facet (if any), otherwise the values retrieved
      // for the facet would collapse to those specified by the filter.
      NameUsageSearchRequest temp = copy.copy();
      temp.removeFilter(facet);
      Query facetQuery = generateQuery(temp);
      var limit = request.getFacetLimit();
      facetAggs.put(facet.name(), Aggregation.of(a -> a
        .filter(facetQuery)
        .aggregations(FACET_AGG_LABEL, Aggregation.of(a2 -> a2
          .terms(t -> t.field(field).size(limit))
        ))
      ));
    }

    // Context filter
    Query contextFilter = getContextFilter();

    // Global → Filter → Facets
    return singletonMap(GLOBAL_AGG_LABEL, Aggregation.of(a -> a
      .global(g -> g)
      .aggregations(FILTER_AGG_LABEL, Aggregation.of(a2 -> a2
        .filter(contextFilter)
        .aggregations(facetAggs)
      ))
    ));
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
