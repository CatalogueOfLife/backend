package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConstantScoreQuery extends AbstractQuery {

  @JsonProperty("constant_score")
  private final Filter filter;

  @JsonCreator
  public ConstantScoreQuery(Filter filter) {
    this.filter = filter;
  }

  public Filter getFilter() {
    return filter;
  }

}
