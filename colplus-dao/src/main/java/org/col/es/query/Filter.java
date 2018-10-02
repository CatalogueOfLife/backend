package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Filter {

  private final TermQuery filter;

  @JsonCreator
  public Filter(TermQuery filter) {
    this.filter = filter;
  }

  public TermQuery getFilter() {
    return filter;
  }

}
