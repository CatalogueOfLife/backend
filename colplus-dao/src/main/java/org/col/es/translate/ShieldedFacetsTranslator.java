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
import static org.col.es.translate.AggregationLabelProvider.getContextFilterLabel;
import static org.col.es.translate.AggregationLabelProvider.getContextLabel;
import static org.col.es.translate.AggregationLabelProvider.getFacetLabel;
import static org.col.es.translate.NameSearchRequestTranslator.generateQuery;

/**
 * A facets translator executing within a execution context separate from the one produced by the main query. No extra filter is applied to
 * constrain the document set in this new execution context (i.e. facets aggregate over the entire index).
 */
public class ShieldedFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  ShieldedFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public Map<String, Aggregation> translate() {
    NameSearchRequest facetFiltersOnly = request.copy();
    GlobalAggregation context = new GlobalAggregation();
    FilterAggregation contextFilter = new FilterAggregation(MatchAllQuery.INSTANCE);
    context.setNestedAggregations(singletonMap(getContextFilterLabel(), contextFilter));
    for (NameSearchParameter facet : facetFiltersOnly.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest copy = facetFiltersOnly.copy();
      copy.removeFilter(facet);
      Query query = isEmpty(copy.getFilters()) ? MatchAllQuery.INSTANCE : generateQuery(copy);
      Aggregation agg = new FacetAggregation(field, query);
      contextFilter.addNestedAggregation(getFacetLabel(facet), agg);
    }
    return singletonMap(getContextLabel(), context);
  }

}
