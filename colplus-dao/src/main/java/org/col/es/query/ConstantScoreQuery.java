package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConstantScoreQuery extends AbstractQuery {

  public static class Filter {
    @JsonProperty("filter")
    private final Query query;

    public Filter(Query query) {
      this.query = query;
    }

    public Query getQuery() {
      return query;
    }
  }

  @JsonProperty("constant_score")
  private final Filter filter;

  public ConstantScoreQuery(Query query) {
    this.filter = new Filter(query);
  }

  public Filter getFilter() {
    return filter;
  }

}
