package org.col.es.dsl;

import java.util.Map;

import static java.util.Collections.singletonMap;

public class TermQuery extends AbstractQuery {

  private final Map<String, TermValue> term;

  public TermQuery(String field, Object value) {
    term = singletonMap(getField(field), new TermValue(value));
  }

  public TermQuery withName(String name) {
    term.values().forEach(t -> t.name(name));
    return this;
  }

  public TermQuery withBoost(Float boost) {
    term.values().forEach(t -> t.boost(boost));
    return this;
  }

  protected String getField(String field) {
    return field;
  }

}
