package org.col.es.query;

import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.col.es.query.SortOptions.Order.ASC;
import static org.col.es.query.SortOptions.Order.DESC;

public class SortField {

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

  @JsonValue
  public Object jsonValue() {
    return options == null ? field : Collections.singletonMap(field, options);
  }

}
