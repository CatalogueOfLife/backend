package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NestedQuery extends AbstractQuery {

  public static enum ScoreMode {
    AVG, SUM, MAX, MIN;

    public String toString() {
      return name().toLowerCase();
    }
  }

  static class Clause {
    final String path;
    @JsonProperty("score_mode")
    final ScoreMode scoreMode;
    final Query query;

    Clause(String path, ScoreMode scoreMode, Query query) {
      this.path = path;
      this.scoreMode = scoreMode;
      this.query = query;
    }
  }

  final Clause nested;

  public NestedQuery(String path, Query query) {
    this.nested = new Clause(path, ScoreMode.AVG, query);
  }

  public NestedQuery(String path, ScoreMode scoreMode, Query query) {
    this.nested = new Clause(path, scoreMode, query);
  }

}
