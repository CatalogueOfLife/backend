package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MatchAllQuery extends AbstractQuery<Constraint> {

  public static final MatchAllQuery INSTANCE = new MatchAllQuery();

  @JsonProperty("match_all")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  private final Constraint constraint = new Constraint();

  @Override
  Constraint getConstraint() {
    return constraint;
  }

}
