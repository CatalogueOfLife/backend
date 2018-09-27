package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConstantScoreQuery implements Query {

  @JsonProperty("constant_score")
  private Query query;

  public Query getQuery() {
    return query;
  }

  public void setQuery(Query query) {
    this.query = query;
  }

}
