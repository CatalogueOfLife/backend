package org.col.es.query;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoolQuery extends AbstractQuery {

  private static class Clause {
    List<Query> must;
    List<Query> filter;
    @JsonProperty("must_not")
    List<Query> mustNot;
    List<Query> should;
  }

  private final Clause bool;

  public BoolQuery() {
    this.bool = new Clause();
  }

  public BoolQuery must(Query query) {
    if (bool.must == null) {
      bool.must = new CollapsibleList<>();
    }
    bool.must.add(query);
    return this;
  }

  public BoolQuery filter(Query query) {
    if (bool.filter == null) {
      bool.filter = new CollapsibleList<>();
    }
    bool.filter.add(query);
    return this;
  }

  public BoolQuery mustNot(Query query) {
    if (bool.mustNot == null) {
      bool.mustNot = new CollapsibleList<>();
    }
    bool.mustNot.add(query);
    return this;
  }

  public BoolQuery should(Query query) {
    if (bool.should == null) {
      bool.should = new CollapsibleList<>();
    }
    bool.should.add(query);
    return this;
  }

}
