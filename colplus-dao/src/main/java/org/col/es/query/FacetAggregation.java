package org.col.es.query;

import static org.col.es.translate.AggregationLabelProvider.getBucketsLabel;

/*
 * N.B. This class does not correspond to any real Elasticsearch aggregation type. It's just a convenience subclass of FilterAggregation
 * particularly suited to facets.
 */
public class FacetAggregation extends FilterAggregation {

  public FacetAggregation(String field, Query filter) {
    super(filter);
    addNestedAggregation(getBucketsLabel(), new TermsAggregation(field));
  }

}
