package org.col.es.query;

import org.col.es.util.CollapsibleTuple;

public class SortField extends CollapsibleTuple<String, SortOptions> {

  public SortField(String field) {
    super(field);
  }

  public SortField(String field, SortOptions options) {
    super(field, options);
  }

}
