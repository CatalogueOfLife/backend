package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the value you are matching your documents against, plus the option to boost matching documents and assign
 * a name to the match condition (enabling a named query mechanism that will tell you if the condition was met for a
 * particular document).
 */
@SuppressWarnings("unused")
public class MatchValue {

  /**
   * Determines how to join the subqueries for the terms in the search phrase.
   *
   */
  public static enum Operator {
    AND, OR;
  }

  private final String query; // The search phrase

  private String _name; // creates named query
  private Float boost;
  private Operator operator;

  MatchValue(String query) {
    this.query = query;
  }

  void boost(Float boost) {
    this.boost = boost;
  }

  void name(String name) {
    this._name = name;
  }

  void operator(Operator operator) {
    this.operator = operator;
  }

}
