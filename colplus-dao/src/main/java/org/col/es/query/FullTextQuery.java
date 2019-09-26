package org.col.es.query;

import org.col.es.mapping.MultiField;

public class FullTextQuery extends AbstractMatchQuery {

  public FullTextQuery(String field, String value) {
    super(field, value);
  }

  protected String getField(String field) {
    return field + "." + MultiField.DEFAULT.getName();
  }

}
