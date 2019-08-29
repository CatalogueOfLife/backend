package org.col.es.dsl;

import org.col.es.ddl.MultiField;

public class FullTextQuery extends TermQuery {
  
  public FullTextQuery(String field, Object value) {
    super(multi(field), value);
  }
  
  public FullTextQuery(String field, String value, Float boost) {
    super(multi(field), value, boost);
  }
  
  private static String multi(String field) {
    return field + "." + MultiField.DEFAULT.getName();
  }
  
}
