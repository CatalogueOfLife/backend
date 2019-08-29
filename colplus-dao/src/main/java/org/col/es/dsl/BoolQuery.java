package org.col.es.dsl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoolQuery extends AbstractQuery {

  private static class Bool {
    List<Query> must;
    List<Query> filter;
    @JsonProperty("must_not")
    List<Query> mustNot;
    List<Query> should;
  }

  private final Bool bool;

  @SuppressWarnings("unused")
  private Float boost;

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

  public BoolQuery boost(float f) {
    this.boost = Float.valueOf(f);
    return this;
  }


}
