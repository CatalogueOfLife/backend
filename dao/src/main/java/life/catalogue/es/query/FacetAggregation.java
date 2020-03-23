package life.catalogue.es.query;

import life.catalogue.es.nu.search.FacetsTranslator;

/**
 * This class is just a convenience subclass of FilterAggregation particularly suited to facets. It does not correspond to any real
 * Elasticsearch aggregation type.
 */
public class FacetAggregation extends FilterAggregation {

  public FacetAggregation(String field, Query filter) {
    super(filter);
    nest(FacetsTranslator.FACET_AGG_LABEL, new TermsAggregation(field));
  }

}
