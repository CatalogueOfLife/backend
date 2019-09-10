package org.col.es.dsl;

import java.util.Map;

import org.col.es.dsl.MatchConstraint.Operator;

import static java.util.Collections.singletonMap;

public abstract class AbstractMatchQuery extends ConstraintQuery<MatchConstraint> {

  private final Map<String, MatchConstraint> match;

  public AbstractMatchQuery(String field, String value) {
    match = singletonMap(getField(field), new MatchConstraint(value));
  }

  public AbstractMatchQuery withOperator(Operator operator) {
    getConstraint().operator(operator);
    return this;
  }

  @Override
  MatchConstraint getConstraint() {
    return match.values().iterator().next();
  }

  abstract String getField(String field);

}
