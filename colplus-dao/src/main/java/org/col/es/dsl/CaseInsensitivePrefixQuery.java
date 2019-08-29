package org.col.es.dsl;

import java.util.Collections;
import java.util.Map;

import org.col.es.ddl.MultiField;

public class CaseInsensitivePrefixQuery extends AbstractQuery {

  final Map<String, TermValue> prefix;

  public CaseInsensitivePrefixQuery(String field, Object value) {
    this(multi(field), value, null);
  }

  public CaseInsensitivePrefixQuery(String field, Object value, Float boost) {
    prefix = Collections.singletonMap(multi(field), new TermValue(value, boost));
  }

  private static String multi(String field) {
    return field + "." + MultiField.IGNORE_CASE.getName();
  }

}
