package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

public class PrefixQuery {

  private Map<String, QueryValue> prefix;

  public PrefixQuery() {}

  public PrefixQuery(String field, Object value) {
    this(field, new QueryValue(value));
  }

  public PrefixQuery(String field, Object value, Float boost) {
    this(field, new QueryValue(value, boost));
  }

  public PrefixQuery(String field, QueryValue query) {
    prefix = new HashMap<>();
    prefix.put(field, query);
  }

  public Map<String, QueryValue> getPrefix() {
    return prefix;
  }

  public void setPrefix(Map<String, QueryValue> prefix) {
    this.prefix = prefix;
  }

}
