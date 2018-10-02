package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

import org.col.es.mapping.MultiField;

public class NGram0Query {

  private Map<String, QueryValue> term;

  public NGram0Query() {}

  public NGram0Query(String field, Object value) {
    this(field, new QueryValue(value));
  }

  public NGram0Query(String field, String value, Float boost) {
    this(multi(field), new QueryValue(value, boost));
  }

  public NGram0Query(String field, QueryValue query) {
    term = new HashMap<>();
    term.put(field, query);
  }

  public Map<String, QueryValue> getTerm() {
    return term;
  }

  public void setTerm(Map<String, QueryValue> term) {
    this.term = term;
  }

  private static String multi(String field) {
    return field + "." + MultiField.NGRAM0_MULTIFIELD.getName();
  }

}
