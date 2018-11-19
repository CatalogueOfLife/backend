package org.col.es.query;

public class BoolFilterAggregation extends FilterAggregation<BoolQuery> {

  public BoolFilterAggregation() {
    super(new BoolQuery());
  }

}
