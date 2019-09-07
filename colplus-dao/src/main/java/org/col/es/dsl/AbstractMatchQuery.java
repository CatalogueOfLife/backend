package org.col.es.dsl;

import java.util.Map;

import org.col.es.dsl.MatchValue.Operator;

import static java.util.Collections.singletonMap;

public abstract class AbstractMatchQuery extends AbstractQuery {

  private final Map<String, MatchValue> match;

  public AbstractMatchQuery(String field, String value) {
    match = singletonMap(getField(field), new MatchValue(value));
  }

  public AbstractMatchQuery withName(String name) {
    match.values().forEach(t -> t.name(name));
    return this;
  }

  public AbstractMatchQuery withBoost(Float boost) {
    match.values().forEach(t -> t.boost(boost));
    return this;
  }

  public AbstractMatchQuery withOperator(Operator operator) {
    match.values().forEach(t -> t.operator(operator));
    return this;
  }

  protected abstract String getField(String field);

}
