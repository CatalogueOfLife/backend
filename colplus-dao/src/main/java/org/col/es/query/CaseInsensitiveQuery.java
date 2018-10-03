package org.col.es.query;

import org.col.es.mapping.MultiField;

public class CaseInsensitiveQuery extends TermQuery {

  public CaseInsensitiveQuery(String field, Object value) {
    super(multi(field), value);
  }

  public CaseInsensitiveQuery(String field, String value, Float boost) {
    super(multi(field),value,boost);
  }

  private static String multi(String field) {
    return field + "." + MultiField.CI_MULTIFIELD.getName();
  }

}
