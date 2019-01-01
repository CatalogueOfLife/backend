package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the value you are matching your documents against, plus the option to boost matching documents.
 */
public class MatchValue {

  public static enum Operator {
    AND, OR;

    @JsonValue
    public String toString() {
      return name();
    }
  }

  // The search string
  final String query;
  final Float boost;
  // How to combine the terms resulting from tokenization
  final Operator operator;

  public MatchValue(String query) {
    this(query, null);
  }

  public MatchValue(String query, Float boost) {
    this(query, boost, Operator.AND);
  }

  public MatchValue(String query, Float boost, Operator operator) {
    this.query = query;
    this.boost = boost;
    this.operator = operator;
  }

}
