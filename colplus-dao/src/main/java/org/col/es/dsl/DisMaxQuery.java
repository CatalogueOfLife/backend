package org.col.es.dsl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DisMaxQuery extends AbstractQuery {

  @SuppressWarnings("unused")
  private static class DisMax {
    private final List<Query> queries = new CollapsibleList<>(8);
    private String _name;
    private Float boost;
    @JsonProperty("tie_breaker")
    private Float tieBreaker;
  }

  @JsonProperty("dis_max")
  private final DisMax disMax;

  public DisMaxQuery() {
    disMax = new DisMax();
  }

  public DisMaxQuery subquery(Query query) {
    disMax.queries.add(query);
    return this;
  }

  public DisMaxQuery withName(String name) {
    disMax._name = name;
    return this;
  }

  public DisMaxQuery withBoost(Float boost) {
    disMax.boost = boost;
    return this;
  }

  public DisMaxQuery withTieBreaker(Float tieBreaker) {
    disMax.tieBreaker = tieBreaker;
    return this;
  }

}
