package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Filter {

  private final Term filter;

  @JsonCreator
  public Filter(Term filter) {
    this.filter = filter;
  }

  public Term getFilter() {
    return filter;
  }

}
