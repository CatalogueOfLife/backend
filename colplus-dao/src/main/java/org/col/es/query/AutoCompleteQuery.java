package org.col.es.query;

import java.util.Collections;
import java.util.Map;

import org.col.es.mapping.MultiField;

public class AutoCompleteQuery extends AbstractQuery {

  final Map<String, MatchValue> match;

  public AutoCompleteQuery(String field, String value) {
    match = Collections.singletonMap(multi(field), new MatchValue(value));
  }

  public AutoCompleteQuery(String field, String value, float boost) {
    match = Collections.singletonMap(multi(field), new MatchValue(value, boost));
  }

  private static String multi(String field) {
    return field + "." + MultiField.AUTO_COMPLETE.getName();
  }

}
