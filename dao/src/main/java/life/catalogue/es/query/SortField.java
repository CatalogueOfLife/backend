package life.catalogue.es.query;

import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonValue;

import static life.catalogue.es.query.SortOptions.Order.ASC;
import static life.catalogue.es.query.SortOptions.Order.DESC;

public class SortField {

  // Sort in the order in which documents were indexed
  public static final SortField DOC = new SortField("_doc");
  public static final SortField SCORE = new SortField("_score");
  public static final SortField RANK = new SortField("rank");
  public static final SortField SCIENTIFIC_NAME = new SortField("scientificName");

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
   * The ES query DSL allows you to either provide a simple string being the field you want to sort on, or the full-blown
   * object modeled by this class. In case of sorting on score ("_score") or index order ("_doc"), you MUST use a simple
   * string. Hence this jsonValue method.
   */
  @JsonValue
  public Object jsonValue() {
    return options == null ? field : Collections.singletonMap(field, options);
  }

}
