package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

public class TermQuery {

  private Map<String, QueryValue> term;

  public TermQuery() {}

  public TermQuery(String field, Object value) {
    this(field, new QueryValue(value));
  }

  public TermQuery(String field, Object value, Float boost) {
    this(field, new QueryValue(value, boost));
  }

  public TermQuery(String field, QueryValue query) {
    term = new HashMap<>();
    term.put(field, query);
  }

  public Map<String, QueryValue> getTerm() {
    return term;
  }

  public void setTerm(Map<String, QueryValue> term) {
    this.term = term;
  }

}
