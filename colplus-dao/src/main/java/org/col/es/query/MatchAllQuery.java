package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MatchAllQuery extends ConstraintQuery<Constraint> {

  @JsonProperty("match_all")
  private final Constraint constraint = new Constraint();

  @Override
  Constraint getConstraint() {
    return constraint;
  }

}
