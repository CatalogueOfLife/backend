package org.col.es.query;

import org.col.es.mapping.MultiField;

public class FullTextQuery extends TermQuery {

  public FullTextQuery(String field, Object value) {
    super(multi(field), value);
  }

  public FullTextQuery(String field, String value, Float boost) {
    super(multi(field), value, boost);
  }

  private static String multi(String field) {
    return field + "." + MultiField.DEFAULT_MULTIFIELD.getName();
  }

}
