package life.catalogue.es.query;

import life.catalogue.es.query.NestedQuery.ScoreMode;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
class NestedConstraint extends Constraint {

  private final String path;
  private final Query query;

  @JsonProperty("score_mode")
  private ScoreMode scoreMode;

  NestedConstraint(String path, Query query) {
    this.path = path;
    this.query = query;
  }

  void scoreMode(ScoreMode scoreMode) {
    this.scoreMode = scoreMode;
  }

}
