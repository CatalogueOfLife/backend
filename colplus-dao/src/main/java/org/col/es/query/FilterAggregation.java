package org.col.es.query;

public class FilterAggregation<T extends Query> extends BucketAggregation {

  private final T filter;

  public FilterAggregation(T filter) {
    super();
    this.filter = filter;
  }

  public T getFilter() {
    return filter;
  }

}
