package org.col.es.dsl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DisMaxQuery extends AbstractQuery {

  @SuppressWarnings("unused")
  private static class DisMax {
    final List<Query> queries = new CollapsibleList<>(8);
    final Float boost; // over-all boost
    @JsonProperty("tie_breaker")
    final Float tieBreaker;

    DisMax(Float boost, Float tieBreaker) {
      this.boost = boost;
      this.tieBreaker = tieBreaker;
    }
  }

  @JsonProperty("dis_max")
  private final DisMax disMax;

  public DisMaxQuery() {
    disMax = new DisMax(null, null);
  }

  public DisMaxQuery(Float boost) {
    disMax = new DisMax(boost, null);
  }

  public DisMaxQuery(Float boost, Float tiebreaker) {
    disMax = new DisMax(boost, tiebreaker);
  }

  public DisMaxQuery subquery(Query query) {
    disMax.queries.add(query);
    return this;
  }

}
