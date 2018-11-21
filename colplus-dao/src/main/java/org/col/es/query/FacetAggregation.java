package org.col.es.query;

public class FacetAggregation extends FilterAggregation {

  public FacetAggregation(String field, Query filter) {
    super(filter);
    addNestedAggregation(field.toUpperCase() + "_UNIQUE_VALUES", new TermsAggregation(field));
  }

}
