package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class NestedQuery extends AbstractQuery {

  public static enum ScoreMode {
    AVG, SUM, MAX, MIN;
    public String toString() {
      return name().toLowerCase();
    }
  }

  private static class Clause extends QueryElement {
    private final String path;
    @JsonProperty("score_mode")
    private final ScoreMode scoreMode;
    private final Query query;

    Clause(String path, ScoreMode scoreMode, Query query) {
      this.path = path;
      this.scoreMode = scoreMode;
      this.query = query;
    }
  }

  private final Clause nested;

  public NestedQuery(String path, Query query) {
    this.nested = new Clause(path, ScoreMode.AVG, query);
  }

  public NestedQuery(String path, ScoreMode scoreMode, Query query) {
    this.nested = new Clause(path, scoreMode, query);
  }

}
