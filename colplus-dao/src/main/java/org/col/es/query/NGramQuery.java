package org.col.es.query;

import org.col.es.mapping.MultiField;

public class NGramQuery extends TermQuery {

  public NGramQuery(String field, Object value) {
    super(multi(field), value);
  }

  public NGramQuery(String field, String value, Float boost) {
    super(multi(field),value,boost);
  }

  private static String multi(String field) {
    return field + "." + MultiField.NGRAM.getName();
  }

}
