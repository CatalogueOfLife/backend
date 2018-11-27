package org.col.es.query;

import java.util.Collections;
import java.util.Map;

public class PrefixQuery extends AbstractQuery {

  final Map<String, TermValue> prefix;

  public PrefixQuery(String field, Object value) {
    this(field, value, null);
  }

  public PrefixQuery(String field, Object value, Float boost) {
    prefix = Collections.singletonMap(field, new TermValue(value, boost));
  }

}
