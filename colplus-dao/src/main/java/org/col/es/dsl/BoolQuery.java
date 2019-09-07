package org.col.es.dsl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoolQuery extends AbstractQuery {

  @SuppressWarnings("unused")
  private static class Bool {
    private List<Query> must;
    private List<Query> filter;
    @JsonProperty("must_not")
    private List<Query> mustNot;
    private List<Query> should;
    private String _name;
    private Float boost;
  }

  private final Bool bool;

  public BoolQuery() {
    this.bool = new Bool();
  }

  public BoolQuery must(Query query) {
    if (bool.must == null) {
      bool.must = new CollapsibleList<>(5);
    }
    bool.must.add(query);
    return this;
  }

  public BoolQuery filter(Query query) {
    if (bool.filter == null) {
      bool.filter = new CollapsibleList<>(5);
    }
    bool.filter.add(query);
    return this;
  }

  public BoolQuery mustNot(Query query) {
    if (bool.mustNot == null) {
      bool.mustNot = new CollapsibleList<>(5);
    }
    bool.mustNot.add(query);
    return this;
  }

  public BoolQuery should(Query query) {
    if (bool.should == null) {
      bool.should = new CollapsibleList<>(5);
    }
    bool.should.add(query);
    return this;
  }

  public BoolQuery withName(String name) {
    bool._name = name;
    return this;
  }

  public BoolQuery withBoost(Float boost) {
    bool.boost = boost;
    return this;
  }

}
