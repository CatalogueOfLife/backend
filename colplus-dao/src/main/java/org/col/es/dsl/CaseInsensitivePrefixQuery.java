package org.col.es.dsl;

import org.col.es.mapping.MultiField;

public class CaseInsensitivePrefixQuery extends PrefixQuery {

  public CaseInsensitivePrefixQuery(String field, Object value) {
    super(field, value);
  }

  protected String getField(String field) {
    return field + "." + MultiField.IGNORE_CASE.getName();
  }

}
