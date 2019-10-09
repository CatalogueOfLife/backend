package org.col.es.query;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Collections.singletonMap;

/**
 * Base class for queries against analyzed fields (as opposed to term queries).
 *
 */
public abstract class AbstractMatchQuery extends ConstraintQuery<MatchConstraint> {

  /**
   * Determines how to join the subqueries for the terms in the search phrase.
   *
   */
  public static enum Operator {
    AND, OR;
    @JsonValue
    public String toString() {
      return name();
    }
  }

  private final Map<String, MatchConstraint> match;

  public AbstractMatchQuery(String field, String value) {
    match = singletonMap(getField(field), new MatchConstraint(value));
  }

  public AbstractMatchQuery withOperator(AbstractMatchQuery.Operator operator) {
    getConstraint().operator(operator);
    return this;
  }

  @Override
  MatchConstraint getConstraint() {
    return match.values().iterator().next();
  }

  abstract String getField(String field);

}
