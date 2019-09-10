package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IsNotNullQuery extends ConstraintQuery<IsNotNullConstraint> {

  @JsonProperty("exists")
  private final IsNotNullConstraint constraint;

  public IsNotNullQuery(String field) {
    this.constraint = new IsNotNullConstraint(field);
  }

  @Override
  IsNotNullConstraint getConstraint() {
    return constraint;
  }

}
