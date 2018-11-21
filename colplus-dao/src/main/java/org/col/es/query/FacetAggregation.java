package org.col.es.query;

public class FacetAggregation extends FilterAggregation {

  public FacetAggregation(String field, Query filter) {
    super(filter);
    addNestedAggregation(field + "_facet", new TermsAggregation(field));
  }

}
