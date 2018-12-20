package org.col.es.query;

import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.col.es.query.SortOptions.Order.ASC;
import static org.col.es.query.SortOptions.Order.DESC;

public class SortField {

  public static final SortField DOC = new SortField("_doc");

  final String field;
  final SortOptions options;

  public SortField(String field) {
    this(field, null);
  }

  public SortField(String field, boolean ascending) {
    this(field, new SortOptions(ascending ? ASC : DESC));
  }

  public SortField(String field, SortOptions options) {
    this.field = field;
    this.options = options;
  }

  /*
   * The ES query DSL allows you to either provide a simple string being the field you want to sort on, or the full-blown object modeled by
   * this class. We mimick this, because it makes the queries easier to read.
   */
  @JsonValue
  public Object jsonValue() {
    return options == null ? field : Collections.singletonMap(field, options);
  }

}
