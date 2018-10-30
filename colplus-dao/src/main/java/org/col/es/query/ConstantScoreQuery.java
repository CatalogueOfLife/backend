package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConstantScoreQuery extends AbstractQuery {

  private static class Filter {
    private final Query query;

    Filter(Query query) {
      this.query = query;
    }

    @JsonProperty("filter")
    public Query getQuery() {
      return query;
    }
  }

  @JsonProperty("constant_score")
  private final Filter filter;

  public ConstantScoreQuery(Query query) {
    this.filter = new Filter(query);
  }

}
