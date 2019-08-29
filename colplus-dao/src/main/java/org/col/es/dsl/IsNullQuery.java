package org.col.es.dsl;

public class IsNullQuery extends BoolQuery {

  public IsNullQuery(String field) {
    super.mustNot(new IsNotNullQuery(field));
  }

  @Override
  public BoolQuery must(Query query) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BoolQuery filter(Query query) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BoolQuery mustNot(Query query) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BoolQuery should(Query query) {
    throw new UnsupportedOperationException();
  }

}
