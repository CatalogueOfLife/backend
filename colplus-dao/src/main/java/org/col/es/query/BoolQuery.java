package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoolQuery extends AbstractQuery {

  Query must;
  @JsonProperty("must_not")
  Query mustNot;
  Query should;

  public Query getMust() {
    return must;
  }

  public void setMust(Query must) {
    this.must = must;
  }

  public Query getMustNot() {
    return mustNot;
  }

  public void setMustNot(Query mustNot) {
    this.mustNot = mustNot;
  }

  public Query getShould() {
    return should;
  }

  public void setShould(Query should) {
    this.should = should;
  }

}
