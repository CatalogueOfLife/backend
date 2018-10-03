package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

public class TermQuery extends AbstractQuery {

  private final Map<String, QueryValue> term;

  public TermQuery(String field, Object value) {
    this(field, value, null);
  }

  public TermQuery(String field, Object value, Float boost) {
    term = new HashMap<>();
    term.put(field, new QueryValue(value, boost));
  }

}
