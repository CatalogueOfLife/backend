package org.col.es.translate;

import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.FacetAggregation;
import org.col.es.query.GlobalAggregation;
import org.col.es.query.Query;
import org.col.es.query.TermsAggregation;

import static java.util.Collections.singletonMap;

import static org.col.common.util.CollectionUtils.isEmpty;
import static org.col.common.util.CollectionUtils.notEmpty;
import static org.col.es.translate.NameSearchRequestTranslator.generateQuery;

/**
 * A facets translator executing within a execution context separate from the one produced by the main query. No extra filter is applied to
 * constrain the document set in this new execution context. This FacetsTranslator is only produced by the FacetsTranslatorFactory if there
 * are at least two facets. However, it could be that only one of them has an associated filter - an edge case for which a simple terms
 * aggregation suffices for that particular facet.
 */
public class ShieldedFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  ShieldedFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public Map<String, Aggregation> translate() {
    NameSearchRequest facetFiltersOnly = request.copy();
    // Remove all filters not corresponding to a facet.
    if (notEmpty(facetFiltersOnly.getFilters())) {
      facetFiltersOnly.getFilters().keySet().retainAll(request.getFacets());
    }
    facetFiltersOnly.setQ(null);
    GlobalAggregation main = new GlobalAggregation();
    for (NameSearchParameter facet : facetFiltersOnly.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest copy = facetFiltersOnly.copy();
      copy.removeFilter(facet);
      if (isEmpty(copy.getFilters())) {
        main.addNestedAggregation(getFacetLabel(field), new TermsAggregation(field));
      } else {
        Query facetFilter = generateQuery(copy);
        main.addNestedAggregation(getFacetLabel(field), new FacetAggregation(field, facetFilter));
      }
    }
    return singletonMap("_ALL_", main);
  }

}
