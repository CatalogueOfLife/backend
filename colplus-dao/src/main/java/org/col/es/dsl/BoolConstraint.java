package org.col.es.dsl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

class BoolConstraint extends Constraint {
  private List<Query> must;
  private List<Query> filter;
  @JsonProperty("must_not")
  private List<Query> mustNot;
  private List<Query> should;

  void must(Query query) {
    if (must == null) {
      must = new CollapsibleList<>(5);
    }
    must.add(query);
  }

  void filter(Query query) {
    if (filter == null) {
      filter = new CollapsibleList<>(5);
    }
    filter.add(query);
  }

  void mustNot(Query query) {
    if (mustNot == null) {
      mustNot = new CollapsibleList<>(5);
    }
    mustNot.add(query);
  }

  void should(Query query) {
    if (should == null) {
      should = new CollapsibleList<>(5);
    }
    should.add(query);
  }
}