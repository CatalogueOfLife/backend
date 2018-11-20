package org.col.es.query;

public class FilterAggregation extends BucketAggregation {

  final Query filter;

  public FilterAggregation(Query filter) {
    this.filter = filter;
  }

}
