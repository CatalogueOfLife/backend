package org.col.es.query;

public class Facet extends FilterAggregation {

  public Facet(String field, Query filter) {
    super(filter);
    addNestedAggregation(field + "_values", new TermsAggregation(field));
  }

}
