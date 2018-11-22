package org.col.es.translate;

import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.FacetAggregation;
import org.col.es.query.FilterAggregation;
import org.col.es.query.GlobalAggregation;
import org.col.es.query.Query;
import org.col.es.query.TermsAggregation;

import static java.util.Collections.singletonMap;

import static org.col.es.translate.NameSearchRequestTranslator.generateQuery;

class FilteredSandboxFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  FilteredSandboxFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public Map<String, Aggregation> translate() {
    NameSearchRequest facetFiltersOnly = request.copy();
    NameSearchRequest otherFiltersOnly = request.copy();
    facetFiltersOnly.getFilters().keySet().retainAll(request.getFacets());
    otherFiltersOnly.getFilters().keySet().removeAll(request.getFacets());
    facetFiltersOnly.setQ(null);
    GlobalAggregation main = new GlobalAggregation();
    Query contextFilter = generateQuery(otherFiltersOnly, false);
    FilterAggregation context = new FilterAggregation(contextFilter);
    main.setNestedAggregations(singletonMap("_CONTEXT_", context));
    for (NameSearchParameter facet : request.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest copy = facetFiltersOnly.copy();
      copy.removeFilter(facet);
      if (copy.getFilters().size() == 0) {
        context.addNestedAggregation(getFacetLabel(field), new TermsAggregation(field));
      } else {
        Query facetFilter = NameSearchRequestTranslator.generateQuery(copy, false);
        context.addNestedAggregation(getFacetLabel(field), new FacetAggregation(field, facetFilter));
      }
    }
    return singletonMap("_ALL_", main);
  }

}
