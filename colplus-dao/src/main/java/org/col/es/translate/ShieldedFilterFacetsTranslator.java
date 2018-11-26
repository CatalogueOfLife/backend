package org.col.es.translate;

import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.FacetAggregation;
import org.col.es.query.FilterAggregation;
import org.col.es.query.GlobalAggregation;
import org.col.es.query.MatchAllQuery;
import org.col.es.query.Query;

import static java.util.Collections.singletonMap;

import static org.col.common.util.CollectionUtils.isEmpty;
import static org.col.common.util.CollectionUtils.notEmpty;
import static org.col.es.translate.AggregationLabelProvider.getContextFilterLabel;
import static org.col.es.translate.AggregationLabelProvider.getContextLabel;
import static org.col.es.translate.AggregationLabelProvider.getFacetLabel;
import static org.col.es.translate.NameSearchRequestTranslator.generateQuery;

/**
 * A facets translator executing within a execution context separate from the one produced by the main query. An extra filter is applied to
 * constrain the document set in this new execution context. This FacetsTranslator is only produced by the FacetsTranslatorFactory if there
 * are at least two facets. However, it could be that only one of them has an associated filter - an edge case for which a simple terms
 * aggregation suffices for that particular facet.
 */
class ShieldedFilterFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  ShieldedFilterFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public Map<String, Aggregation> translate() {
    NameSearchRequest facetFiltersOnly = request.copy();
    NameSearchRequest otherFiltersOnly = request.copy();
    if (notEmpty(request.getFilters())) {
      facetFiltersOnly.getFilters().keySet().retainAll(request.getFacets());
      otherFiltersOnly.getFilters().keySet().removeAll(request.getFacets());
    }
    facetFiltersOnly.setQ(null);
    GlobalAggregation context = new GlobalAggregation();
    Query query = generateQuery(otherFiltersOnly);
    FilterAggregation contextFilter = new FilterAggregation(query);
    context.setNestedAggregations(singletonMap(getContextFilterLabel(), contextFilter));
    for (NameSearchParameter facet : facetFiltersOnly.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest copy = facetFiltersOnly.copy();
      copy.removeFilter(facet);
      /*
       * NB When no filters are left, we could as well use a simple TermsAggregation, but that would make the query response less uniform,
       * so harder to parse.
       */
      query = isEmpty(copy.getFilters()) ? MatchAllQuery.INSTANCE : generateQuery(copy);
      Aggregation agg = new FacetAggregation(field, query);
      contextFilter.addNestedAggregation(getFacetLabel(facet), agg);
    }
    return singletonMap(getContextLabel(), context);
  }

}
