package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonCreator;

public class TermsAggregation extends AbstractAggregation {

  public static class Terms {
    final String field;
    final Integer size;

    @JsonCreator
    Terms(String field, Integer size) {
      this.field = field;
      this.size = size;
    }
  }

  private final Terms terms;

  public TermsAggregation(String field) {
    this.terms = new Terms(field, null);
  }

  public TermsAggregation(String field, Integer size) {
    this.terms = new Terms(field, size);
  }

  Terms getTerms() {
    return terms;
  }

}
