package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MatchAllQuery extends AbstractQuery<Constraint> {

  @JsonProperty("match_all")
  private final Constraint constraint = new Constraint();

  @Override
  Constraint getConstraint() {
    return constraint;
  }

}
