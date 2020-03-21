package life.catalogue.es.query;

import life.catalogue.es.nu.search.FacetsTranslator;

/*
 * N.B. This class does not correspond to any real Elasticsearch aggregation type. It's just a convenience subclass of FilterAggregation
 * particularly suited to facets.
 */
public class FacetAggregation extends FilterAggregation {

  public FacetAggregation(String field, Query filter) {
    super(filter);
    nest(FacetsTranslator.FACET_AGG_LABEL, new TermsAggregation(field));
  }

}
