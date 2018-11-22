package org.col.es.translate;

import java.util.Map;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.FacetAggregation;
import org.col.es.query.GlobalAggregation;
import org.col.es.query.Query;

import static java.util.Collections.singletonMap;

public class SandboxFacetsTranslator implements FacetsTranslator {

  private final NameSearchRequest request;

  public SandboxFacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public Map<String, Aggregation> translate() {
    NameSearchRequest base = request.copy();
    base.getFilters().keySet().retainAll(request.getFacets());
    base.setQ(null);
    GlobalAggregation main = new GlobalAggregation();
    for (NameSearchParameter facet : request.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest copy = base.copy();
      copy.removeFilter(facet);
      Query facetFilter = NameSearchRequestTranslator.generateQuery(copy, false);
      main.addNestedAggregation(getFacetLabel(field), new FacetAggregation(field, facetFilter));
    }
    return singletonMap("ALL", main);
  }

}
