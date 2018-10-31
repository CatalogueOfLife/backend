package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

public class PrefixQuery extends AbstractQuery {

  private final Map<String, QueryValue> prefix;

  public PrefixQuery(String field, Object value) {
    this(field, value, null);
  }

  public PrefixQuery(String field, Object value, Float boost) {
    prefix = new HashMap<>();
    prefix.put(field, new QueryValue(value, boost));
  }

  Map<String, QueryValue> getPrefix() {
    return prefix;
  }

}
