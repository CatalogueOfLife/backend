package org.col.es.translate;

import java.util.Set;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;

import static org.col.common.util.CollectionUtils.*;

/**
 * Produced a FacetsTranslator instance based on the state of the NameSearchRequest. Don't directly instantiate a FacetsTranslator class b/c
 * they simply presume the (pre)conditions checked by the factory to be holding. I.e. you will get NPEs c.q. invalid queries if you do.
 */
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
       * that facet) or whether there are any other filters at all. We simply need to retrieve all unique values (pagination considerations
       * aside) for that facet, and we can do it within the current execution context.
       */
      return new SimpleFacetsTranslator(request);
    }
    if (isEmpty(filters) && StringUtils.isEmpty(request.getQ())) {
      /*
       * There are no active filters. We must simply retrieve all unique values for all facets. No separate execution context required.
       */
      return new SimpleFacetsTranslator(request);
    }
    if (facets.containsAll(filters) && StringUtils.isEmpty(request.getQ())) {
      /*
       * There are multiple active filters, but they all correspond to facets. We need a separate execution context, because in the main
       * query we apply all filters at the same time, while we must successively disable each filter when dealing with the corresponding facet.
       * However we don't need to specify an over-arching filter for the new execution context.
       */
      return new ShieldedFacetsTranslator(request);
    }
    /*
     * There are multiple facets and one or more filters do not correspond to any facet. We need a separate execution context and we need to
     * apply all non-facet filters to constrain the document set for that context.
     */
    return new ShieldedFilterFacetsTranslator(request);
  }

}
