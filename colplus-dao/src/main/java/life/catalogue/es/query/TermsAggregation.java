package life.catalogue.es.query;

import java.util.Collections;
import java.util.Map;

public class TermsAggregation extends BucketAggregation {

  public static enum SortBy {
    DOC_COUNT_DESC, VALUE_ASC
  }

  public static class Terms {
    // The GROUP BY field
    final String field;
    // The maximum number of buckets (distinct values) to retrieve
    final Integer size;
    final Map<String, String> order;

    Terms(String field, Integer size, Map<String, String> order) {
      this.field = field;
      this.size = size;
      this.order = order;
    }
  }

  private static final int DEFAULT_NUM_BUCKETS = 50;
  private static final Map<String, String> SORTBY_DOC_COUNT_DESC = Collections.singletonMap("doc_count", "desc");
  private static final Map<String, String> SORTBY_VALUE_ASC = Collections.singletonMap("_key", "asc");

  final Terms terms;

  public TermsAggregation(String field) {
    this.terms = new Terms(field, DEFAULT_NUM_BUCKETS, null);
  }

  public TermsAggregation(String field, Integer size) {
    this.terms = new Terms(field, size, null);
  }

  public TermsAggregation(String field, Integer size, SortBy sortBy) {
    this.terms = new Terms(field, size, translate(sortBy));
  }

  private static Map<String, String> translate(SortBy sortBy) {
    if (sortBy == null) {
      return null;
    }
    if (sortBy == SortBy.VALUE_ASC) {
      return SORTBY_VALUE_ASC;
    }
    return SORTBY_DOC_COUNT_DESC;
  }

}
