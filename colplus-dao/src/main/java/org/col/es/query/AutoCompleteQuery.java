package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

import org.col.es.mapping.MultiField;

public class AutoCompleteQuery extends AbstractQuery {
  
  private final Map<String, MatchValue> match;
  
  public AutoCompleteQuery(String field, String value) {
    match = new HashMap<>();
    match.put(multi(field), new MatchValue(value));
  }
  
  private static String multi(String field) {
    return field + "." + MultiField.AUTO_COMPLETE.getName();
  }
  
}
