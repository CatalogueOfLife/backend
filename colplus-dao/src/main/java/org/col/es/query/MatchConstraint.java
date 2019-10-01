package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the value you are matching your documents against (using tokenization and scoring as in full-text
 * queries).
 */
@SuppressWarnings("unused")
class MatchConstraint extends Constraint {

  /**
   * Determines how to join the subqueries for the terms in the search phrase.
   *
   */
  public static enum Operator {
    AND, OR;
  }

  private final String query; // The search phrase
  
  private Operator operator;

  MatchConstraint(String query) {
    this.query = query;
  }

  void operator(Operator operator) {
    this.operator = operator;
  }

}
