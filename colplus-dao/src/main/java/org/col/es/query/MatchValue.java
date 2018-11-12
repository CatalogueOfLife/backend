package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonValue;

public class MatchValue {
  
  public static enum Operator {
    AND,
    OR;
    
    @JsonValue
    public String toString() {
      return name();
    }
  }
  
  // This is actually just the search string
  private final String query;
  private final Float boost;
  private final Operator operator;
  
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
  
  String getQuery() {
    return query;
  }
  
  Float getBoost() {
    return boost;
  }
  
  Operator getOperator() {
    return operator;
  }
  
}
