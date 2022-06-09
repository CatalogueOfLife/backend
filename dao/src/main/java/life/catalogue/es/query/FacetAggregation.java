package life.catalogue.es.query;

import life.catalogue.es.nu.search.FacetsTranslator;

/**
 * This class is just a convenience subclass of FilterAggregation particularly suited to facets. It does not correspond to any real
 * Elasticsearch aggregation type.
 */
public class FacetAggregation extends FilterAggregation {
  private static final int DEFAULT_NUM_BUCKETS = 50;

  public FacetAggregation(String field, Query filter, Integer facetLimit) {
    super(filter);
    nest(FacetsTranslator.FACET_AGG_LABEL, new TermsAggregation(field, facetLimit == null ? DEFAULT_NUM_BUCKETS : facetLimit));
  }
}
