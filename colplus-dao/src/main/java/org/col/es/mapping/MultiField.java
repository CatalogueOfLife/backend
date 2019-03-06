package org.col.es.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code MultiField} is a virtual field underneath a {@link SimpleField regular field} that specifies an extra analyzer to be applied to
 * the data stored in that field. Note that, although {@code MultiField} is a subclass of {@link ESField}, calling {@link #getParent()} on a
 * {@code MultiField} will return {@code null}. In other words, you can navigate from a {@link SimpleField} to its {@code MultiField}
 * children, but not the other way round.
 */
public class MultiField extends ESField {

  /**
   * A string field analyzed using the default (full-text) analyzer.
   */
  public static final MultiField DEFAULT;
  /**
   * A string field for case-insensitive comparisons.
   */
  public static final MultiField IGNORE_CASE;
  /**
   * A string field for case-insensitive "contains" or "LIKE" comparisons.
   */
  public static final MultiField AUTO_COMPLETE;

  static {
    DEFAULT = new MultiField("ft", null);
    IGNORE_CASE = new MultiField("ic", "ignore_case");
    AUTO_COMPLETE = new MultiField("ac", "autocomplete", "autocomplete_search");
  }

  private final String analyzer;
  @JsonProperty("search_analyzer")
  private final String searchAnalyzer;

  private MultiField(String name, String analyzer) {
    this(name, analyzer, null);
  }

  /*
   * NB analyzer and searchAnalyzer must match analyzers defined in es-settings.json!
   */
  private MultiField(String name, String analyzer, String searchAnalyzer) {
    super();
    this.type = ESDataType.TEXT;
    this.name = name;
    this.analyzer = analyzer;
    this.searchAnalyzer = searchAnalyzer;
  }

  public String getAnalyzer() {
    return analyzer;
  }

  public String getSearchAnalyzer() {
    return searchAnalyzer;
  }

}
