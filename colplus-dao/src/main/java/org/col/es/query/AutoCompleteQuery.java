package org.col.es.query;

import org.col.es.mapping.MultiField;

public class AutoCompleteQuery extends TermQuery {

  public AutoCompleteQuery(String field, Object value) {
    super(multi(field), value);
  }

  public AutoCompleteQuery(String field, String value, Float boost) {
    super(multi(field),value,boost);
  }

  private static String multi(String field) {
    return field + "." + MultiField.AUTO_COMPLETE.getName();
  }

}
