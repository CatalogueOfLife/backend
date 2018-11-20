package org.col.es.query;

public class TermsAggregation extends BucketAggregation {

  public static class Terms {
    // The GROUP BY field
    final String field;
    // The maximum number of buckets (distinct values) to retrieve
    final Integer size;

    Terms(String field, Integer size) {
      this.field = field;
      this.size = size;
    }
  }

  final Terms terms;

  public TermsAggregation(String field) {
    this.terms = new Terms(field, null);
  }

  public TermsAggregation(String field, Integer size) {
    this.terms = new Terms(field, size);
  }

}
