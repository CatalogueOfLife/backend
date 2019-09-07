package org.col.es.dsl;

import java.util.Map;

import static java.util.Collections.singletonMap;

public class PrefixQuery extends AbstractQuery {

  private final Map<String, TermValue> prefix;

  public PrefixQuery(String field, Object value) {
    prefix = singletonMap(getField(field), new TermValue(value));
  }

  public PrefixQuery withName(String name) {
    prefix.values().forEach(t -> t.name(name));
    return this;
  }

  public PrefixQuery withBoost(Float boost) {
    prefix.values().forEach(t -> t.boost(boost));
    return this;
  }


  protected String getField(String field) {
    return field;
  }

}
