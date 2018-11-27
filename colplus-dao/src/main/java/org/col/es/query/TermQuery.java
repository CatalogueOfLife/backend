package org.col.es.query;

import java.util.Collections;
import java.util.Map;

public class TermQuery extends AbstractQuery {

  final Map<String, TermValue> term;

  public TermQuery(String field, Object value) {
    this(field, value, null);
  }

  public TermQuery(String field, Object value, Float boost) {
    term = Collections.singletonMap(field, new TermValue(value, boost));
  }

}
