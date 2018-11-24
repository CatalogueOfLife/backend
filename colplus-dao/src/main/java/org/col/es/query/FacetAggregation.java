package org.col.es.query;

/*
 * N.B. This class does not correspond to any real Elasticsearch aggregation type. It's a convenience subclass of FilterAggregation particularly
 * suited to facets.
 */
public class FacetAggregation extends FilterAggregation {

  public FacetAggregation(String field, Query filter) {
    super(filter);
    addNestedAggregation(field.toUpperCase() + "_UNIQUE_VALUES", new TermsAggregation(field));
  }

}
