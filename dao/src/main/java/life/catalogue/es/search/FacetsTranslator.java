package life.catalogue.es.search;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.FieldLookup;

import java.util.LinkedHashMap;
import java.util.Map;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static java.util.Collections.singletonMap;
import static life.catalogue.es.search.SearchRequestTranslator.generateQuery;

/**
 * Translates the facets of a NameUsageSearchRequest into aggregations.
 *
 * A facet is aggregated inside the query context, as a plain top level terms aggregation over the documents the main
 * query matches, unless the request also filters on that very facet. Then its counts have to ignore the facet's own
 * filter so the user can switch to another value, and the only place to compute that is the global context: a global
 * aggregation, the main query minus the facet filters as a filter aggregation, and under it one filter aggregation per
 * facet carrying every facet filter but its own. The global context visits every document of every shard whatever the
 * query, so it is reserved for requests that need it. The taxon page request - TAXON_ID and status filters, a rank
 * facet - does not, and neither does a request with {@code facetIncludeSelf}, whose counts are those of the full query.
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
    return needsGlobalContext() ? translateGlobal() : translateInQueryContext();
  }

  /**
   * True when a facet has to ignore its own filter: the request filters on a facet parameter and does not ask for
   * facetIncludeSelf.
   */
  boolean needsGlobalContext() {
    if (Boolean.TRUE.equals(request.getFacetIncludeSelf())) {
      return false;
    }
    for (NameUsageSearchParameter facet : request.getFacets()) {
      if (request.hasFilter(facet)) {
        return true;
      }
    }
    return false;
  }

  private Map<String, Aggregation> translateInQueryContext() {
    Map<String, Aggregation> aggs = new LinkedHashMap<>();
    for (NameUsageSearchParameter facet : request.getFacets()) {
      aggs.put(facet.name(), termsAggregation(facet));
    }
    return aggs;
  }

  private Map<String, Aggregation> translateGlobal() {
    NameUsageSearchRequest copy = request.copy();
    copy.getFilters().keySet().retainAll(request.getFacets());
    copy.setQ(null);

    // Build per-facet aggregations
    Map<String, Aggregation> facetAggs = new LinkedHashMap<>();
    for (NameUsageSearchParameter facet : copy.getFacets()) {
      // Temporarily remove the filter corresponding to the facet (if any), otherwise the values retrieved
      // for the facet would collapse to those specified by the filter.
      NameUsageSearchRequest temp = copy.copy();
      temp.removeFilter(facet);
      Query facetQuery = generateQuery(temp);
      facetAggs.put(facet.name(), Aggregation.of(a -> a
        .filter(facetQuery)
        .aggregations(FACET_AGG_LABEL, termsAggregation(facet))
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

  private Aggregation termsAggregation(NameUsageSearchParameter facet) {
    String field = FieldLookup.INSTANCE.lookupSingle(facet);
    var limit = request.getFacetLimit();
    var offset = request.getFacetOffset();
    var minCnt = request.getFacetMinCount();
    // ES terms aggregation has no native offset; request limit+offset so the response translator can skip the first offset entries
    Integer effectiveSize = (limit != null || offset != null)
        ? (limit != null ? limit : 0) + (offset != null ? offset : 0)
        : null;
    return Aggregation.of(a -> a
      .terms(t -> t
        .field(field)
        .minDocCount(minCnt)
        .size(effectiveSize)
      )
    );
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
