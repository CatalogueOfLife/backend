package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NestedQuery extends ConstraintQuery<NestedConstraint> {

  public static enum ScoreMode {
    AVG, SUM, MAX, MIN;
  }

  @JsonProperty("nested")
  private final NestedConstraint constraint;

  public NestedQuery(String path, Query query) {
    this.constraint = new NestedConstraint(path, query);
  }

  public NestedQuery withScoreMode(ScoreMode scoreMode) {
    constraint.scoreMode(scoreMode);
    return this;
  }

  @Override
  NestedConstraint getConstraint() {
    return constraint;
  }

}
