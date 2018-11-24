package org.col.es.translate;

import java.util.Set;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;

import static org.col.common.util.CollectionUtils.isEmpty;
import static org.col.common.util.CollectionUtils.notEmpty;

class FacetsTranslatorFactory {

  private final NameSearchRequest request;

  FacetsTranslatorFactory(NameSearchRequest request) {
    this.request = request;
  }

  FacetsTranslator createTranslator() {
    Preconditions.checkArgument(notEmpty(request.getFacets()), "Should have verified presence of facets first");
    Set<NameSearchParameter> facets = request.getFacets();
    Set<NameSearchParameter> filters = request.getFilters().keySet();
    if (facets.size() == 1) {
      /*
       * There is just one facet. It doesn't matter if there is a corresponding filter (i.e. the user has selected one or more values from
       * that facet) or whether there are any other filters at all; we simply need to retrieve all distinct values (pagination
       * considerations aside) for that facet, given the current execution context.
       */
      return new SimpleFacetsTranslator(request);
    }
    if (facets.containsAll(filters) && StringUtils.isEmpty(request.getQ())) {
      /*
       * There are multiple active filters, but they all correspond to facets. We need a separate execution context, because in the main
       * query we apply all filters, while we successively disable each of those filters when retrieving the unique values of the facets.
       * However we don't need to specify a filter constraining the document set over which to aggregate.
       */
      return new ShieldedFacetsTranslator(request);
    }
    /*
     * There are multiple facets and one or more filters are not corresponding to any facet. We need a separate execution context and we
     * need to apply all non-facet filters to constrain the document set for that context.
     */
    return new ShieldedFilterFacetsTranslator(request);
  }

}
