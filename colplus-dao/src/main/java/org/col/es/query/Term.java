package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

public class Term {

  private Map<String, TermQuery> term;

  public Term() {}

  public Term(String field, Object value) {
    this(field, new TermQuery(value));
  }

  public Term(String field, Object value, Float boost) {
    this(field, new TermQuery(value, boost));
  }

  public Term(String field, TermQuery query) {
    term = new HashMap<>();
    term.put(field, query);
  }

  public Map<String, TermQuery> getTerm() {
    return term;
  }

  public void setTerm(Map<String, TermQuery> term) {
    this.term = term;
  }

}
